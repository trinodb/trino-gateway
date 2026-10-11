// Semi UI needs this adapter for React 19. It has to be the first import,
// before any Semi component is loaded.
import '@douyinfe/semi-ui/react19-adapter'
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
