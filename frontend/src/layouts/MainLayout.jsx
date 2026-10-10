
import { Outlet } from 'react-router-dom';

function MainLayout() {
  return (
    <div className="app-layout">
      <header className="app-header">
        <a className="app-brand" href="/">
          <span className="brand-mark">S</span>
          <span>Charm Quiz System</span>
        </a>

        <nav className="app-nav">
          <a href="/">Trang chủ</a>
        </nav>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <p>Charm Quiz System</p>
      </footer>
    </div>
  );
}

export default MainLayout;
