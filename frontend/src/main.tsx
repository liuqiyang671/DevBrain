/**
 * DevBrain-CQUPT 前端应用入口模块
 * 负责初始化 React 应用并挂载到 DOM
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import './styles.css';

/**
 * 创建 React 根节点并渲染应用
 * 使用 React.StrictMode 启用严格模式，帮助发现潜在问题
 */
ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
