import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import App from './App.jsx';

describe('BookMind App bootstrap shell', () => {
  it('renders navigation tabs and switches views cleanly', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ status: 'UP', spiApiVersion: '1.0', llmConfigured: false })
    });

    render(<App />);

    expect(screen.getByText('BookMind')).toBeInTheDocument();
    expect(await screen.findByText('UP')).toBeInTheDocument();
    expect(screen.getByText(/个人书架/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'AI 伴读' }));
    expect(screen.getByText(/严格分区展示/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '插件管理' }));
    expect(screen.getByText(/扩展插件管理/)).toBeInTheDocument();
  });
});
