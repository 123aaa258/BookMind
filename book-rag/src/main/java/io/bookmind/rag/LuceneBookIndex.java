package io.bookmind.rag;

import io.bookmind.rag.model.SearchHit;
import io.bookmind.rag.model.TextChunk;
import io.bookmind.spi.ParsedBook;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleFragmenter;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Local Apache Lucene BM25 index for chapter chunks.
 *
 * <p>Uses {@link SmartChineseAnalyzer} for Chinese tokenization and Lucene's default
 * {@code BM25Similarity} for ranking. Documents are replaced per {@code bookId}, so
 * re-scanning a library never duplicates chunks.
 */
public class LuceneBookIndex implements Closeable {

    private static final String FIELD_CHUNK_ID = "chunkId";
    private static final String FIELD_BOOK_ID = "bookId";
    private static final String FIELD_BOOK_TITLE = "bookTitle";
    private static final String FIELD_CHAPTER_INDEX = "chapterIndex";
    private static final String FIELD_CHAPTER_TITLE = "chapterTitle";
    private static final String FIELD_LOCATOR = "locator";
    private static final String FIELD_TEXT = "text";

    private static final String[] SEARCH_FIELDS = {FIELD_TEXT, FIELD_BOOK_TITLE, FIELD_CHAPTER_TITLE};
    private static final Map<String, Float> SEARCH_BOOSTS = Map.of(
            FIELD_TEXT, 2.0f,
            FIELD_BOOK_TITLE, 1.0f,
            FIELD_CHAPTER_TITLE, 1.2f
    );
    private static final int SNIPPET_MAX_CHARS = 120;

    private final Directory directory;
    private final Analyzer analyzer;
    private final IndexWriter writer;
    private final ChapterChunker chunker;
    private final Object lock = new Object();

    /**
     * Opens (or creates) a file-system index at {@code indexPath}.
     *
     * @param indexPath {@code bookmind.data-dir/lucene-index}
     * @throws IOException when the directory or index cannot be opened
     */
    public LuceneBookIndex(Path indexPath) throws IOException {
        this(FSDirectory.open(Files.createDirectories(indexPath)), new SmartChineseAnalyzer(), new ChapterChunker());
    }

    /**
     * Opens an index over an arbitrary Lucene {@link Directory} (used by tests with an in-memory directory).
     *
     * @param directory Lucene directory
     * @param chunker   chapter chunker
     * @throws IOException when the index writer cannot be opened
     */
    public LuceneBookIndex(Directory directory, ChapterChunker chunker) throws IOException {
        this(directory, new SmartChineseAnalyzer(), chunker);
    }

    public LuceneBookIndex(Directory directory, Analyzer analyzer, ChapterChunker chunker) throws IOException {
        this.directory = Objects.requireNonNull(directory, "directory must not be null");
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer must not be null");
        this.chunker = Objects.requireNonNull(chunker, "chunker must not be null");
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.writer = new IndexWriter(directory, config);
    }

    /**
     * Slices and indexes all chapters of a book, replacing any previous chunks of the same book.
     *
     * @param bookId stable library book identifier
     * @param book   parsed book
     */
    public void indexBook(String bookId, ParsedBook book) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        Objects.requireNonNull(book, "book must not be null");
        List<TextChunk> chunks = chunker.chunk(bookId, book);
        synchronized (lock) {
            try {
                writer.deleteDocuments(new Term(FIELD_BOOK_ID, bookId));
                for (TextChunk chunk : chunks) {
                    writer.addDocument(toDocument(chunk));
                }
                writer.commit();
            } catch (IOException e) {
                throw new RagException(RagErrorCode.INDEX_ERROR, "Failed to index book " + bookId, e);
            }
        }
    }

    /**
     * Removes every indexed chunk of one book.
     *
     * @param bookId library book identifier
     */
    public void removeBook(String bookId) {
        Objects.requireNonNull(bookId, "bookId must not be null");
        synchronized (lock) {
            try {
                writer.deleteDocuments(new Term(FIELD_BOOK_ID, bookId));
                writer.commit();
            } catch (IOException e) {
                throw new RagException(RagErrorCode.INDEX_ERROR, "Failed to remove book " + bookId, e);
            }
        }
    }

    /**
     * Runs a BM25 search over chunk text, book title and chapter title.
     *
     * @param query          raw user query; blank queries return an empty list
     * @param optionalBookId optional scope filter
     * @param topK           maximum number of hits, must be positive
     * @return ranked hits with highlighted snippets
     */
    public List<SearchHit> search(String query, Optional<String> optionalBookId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be > 0");
        }
        Optional<String> scope = optionalBookId == null ? Optional.empty() : optionalBookId;
        Query luceneQuery = buildQuery(query, scope);

        synchronized (lock) {
            try (DirectoryReader reader = DirectoryReader.open(writer)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs topDocs = searcher.search(luceneQuery, topK);
                Highlighter highlighter = newHighlighter(luceneQuery);

                List<SearchHit> hits = new ArrayList<>(topDocs.scoreDocs.length);
                for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                    Document document = searcher.storedFields().document(scoreDoc.doc);
                    hits.add(new SearchHit(
                            document.get(FIELD_CHUNK_ID),
                            document.get(FIELD_BOOK_ID),
                            document.get(FIELD_BOOK_TITLE),
                            Integer.parseInt(document.get(FIELD_CHAPTER_INDEX)),
                            document.get(FIELD_CHAPTER_TITLE),
                            document.get(FIELD_LOCATOR),
                            snippet(highlighter, document.get(FIELD_TEXT)),
                            scoreDoc.score
                    ));
                }
                return List.copyOf(hits);
            } catch (IOException e) {
                throw new RagException(RagErrorCode.INDEX_ERROR, "Failed to search Lucene index", e);
            }
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            try {
                writer.close();
            } finally {
                try {
                    analyzer.close();
                } finally {
                    directory.close();
                }
            }
        }
    }

    private Document toDocument(TextChunk chunk) {
        Document document = new Document();
        document.add(new StringField(FIELD_CHUNK_ID, chunk.chunkId(), Field.Store.YES));
        document.add(new StringField(FIELD_BOOK_ID, chunk.bookId(), Field.Store.YES));
        document.add(new TextField(FIELD_BOOK_TITLE, chunk.bookTitle(), Field.Store.YES));
        document.add(new StringField(FIELD_CHAPTER_INDEX, Integer.toString(chunk.chapterIndex()), Field.Store.YES));
        document.add(new TextField(FIELD_CHAPTER_TITLE, chunk.chapterTitle(), Field.Store.YES));
        document.add(new StringField(FIELD_LOCATOR, chunk.locator(), Field.Store.YES));
        document.add(new TextField(FIELD_TEXT, chunk.text(), Field.Store.YES));
        return document;
    }

    private Query buildQuery(String rawQuery, Optional<String> bookId) {
        try {
            MultiFieldQueryParser parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer, SEARCH_BOOSTS);
            Query textQuery = parser.parse(QueryParser.escape(rawQuery));
            if (bookId.isEmpty()) {
                return textQuery;
            }
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            builder.add(textQuery, BooleanClause.Occur.MUST);
            builder.add(new TermQuery(new Term(FIELD_BOOK_ID, bookId.get())), BooleanClause.Occur.FILTER);
            return builder.build();
        } catch (ParseException e) {
            throw new RagException(RagErrorCode.INDEX_ERROR, "Invalid search query: " + rawQuery, e);
        }
    }

    private Highlighter newHighlighter(Query query) {
        Highlighter highlighter = new Highlighter(
                new SimpleHTMLFormatter("<mark>", "</mark>"),
                new QueryScorer(query, FIELD_TEXT)
        );
        highlighter.setTextFragmenter(new SimpleFragmenter(SNIPPET_MAX_CHARS));
        return highlighter;
    }

    private String snippet(Highlighter highlighter, String text) {
        try {
            String fragment = highlighter.getBestFragment(analyzer, FIELD_TEXT, text);
            return fragment == null ? abbreviate(text) : fragment;
        } catch (IOException | InvalidTokenOffsetsException e) {
            return abbreviate(text);
        }
    }

    private String abbreviate(String text) {
        return text.length() <= SNIPPET_MAX_CHARS ? text : text.substring(0, SNIPPET_MAX_CHARS);
    }
}
