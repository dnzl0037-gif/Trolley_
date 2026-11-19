import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { setupDeepLinks } from './lib/nativeAuth';
import './styles.css';

setupDeepLinks();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode><App /></React.StrictMode>,
);

const splash = document.getElementById('splash');
if (splash) {
  setTimeout(() => {
    splash.classList.add('hide');
    setTimeout(() => splash.remove(), 500);
  }, 2400);
}
