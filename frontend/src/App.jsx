import React, { useEffect, useState } from 'react';

const NAV_TABS = [
  { id: 'shelf', label: '书架' },
  { id: 'reader', label: '阅读器' },
  { id: 'companion', label: 'AI 伴读' },
  { id: 'plugins', label: '插件管理' }
];

export default function App() {
  const [activeTab, setActiveTab] = useState('shelf');
  const [health, setHealth] = useState({ status: 'CHECKING', spiApiVersion: '-', llmConfigured: false });

  useEffect(() => {
    let cancelled = false;
    fetch('/api/health')
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(`HTTP ${res.status}`))))
      .then((data) => {
        if (!cancelled) {
          setHealth(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setHealth({ status: 'OFFLINE', spiApiVersion: '-', llmConfigured: false });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="bm-shell">
      <header className="bm-header">
        <div className="bm-brand">
          <img src="/logo.png" alt="BookMind logo" className="bm-logo" />
          <div>
            <h1>BookMind</h1>
            <p>本地私人电子书库与 AI 伴读服务</p>
          </div>
        </div>
        <nav className="bm-nav" aria-label="主导航">
          {NAV_TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              className={activeTab === tab.id ? 'bm-tab active' : 'bm-tab'}
              onClick={() => setActiveTab(tab.id)}
            >
              {tab.label}
            </button>
          ))}
        </nav>
        <div className="bm-status" data-testid="health-badge">
          后端状态: <strong>{health.status}</strong> · SPI v{health.spiApiVersion}
        </div>
      </header>

      <main className="bm-main">
        {activeTab === 'shelf' && (
          <section className="bm-card">
            <h2>个人书架（Role 1 · PR-F1）</h2>
            <p>展示扫描到的本地 TXT 与外部插件扩展的 PDF 书籍封面、作者与格式标签。</p>
          </section>
        )}
        {activeTab === 'reader' && (
          <section className="bm-card">
            <h2>在线章节阅读器（Role 1 · PR-F2）</h2>
            <p>支持目录切换、阅读进度记录，以及从 AI 伴读引用角标直达定位高亮。</p>
          </section>
        )}
        {activeTab === 'companion' && (
          <section className="bm-card">
            <h2>AI 伴读问答（Role 1 · PR-F3）</h2>
            <p>严格分区展示「OpenAI 兼容模型回答」与「本地 Lucene 检索原文证据」，点击引用直达原文。</p>
          </section>
        )}
        {activeTab === 'plugins' && (
          <section className="bm-card">
            <h2>扩展插件管理（Role 1 · PR-F4）</h2>
            <p>直观查看内置解析器与外部 plugins/ 目录中发现的格式插件状态、版本与隔离诊断信息。</p>
          </section>
        )}
      </main>
    </div>
  );
}
