import React from "react";
import ReactDOM from "react-dom/client";
import EditorApp from "./EditorApp";

// 正式编辑器入口（Android WebView 容器加载）
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <EditorApp />
  </React.StrictMode>
);
