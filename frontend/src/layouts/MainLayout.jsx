
import { useRef, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';

function MainLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const toggleRef = useRef(null);

  function handleHeaderKeyDown(event) {
    if (event.key === 'Escape' && menuOpen) {
      setMenuOpen(false);
      toggleRef.current?.focus();
    }
  }

  return (
    <div className="app-layout">
      <header className="app-header" onKeyDown={handleHeaderKeyDown}>
        <div className="app-header__inner">
          <NavLink className="app-brand" to="/" aria-label="Về trang chủ Your Story Your Charm" onClick={() => setMenuOpen(false)}>
            <span className="brand-mark"><img src="/dong-son-drum.png" alt="" width="40" height="40" /></span>
            <span>Your Story Your Charm</span>
          </NavLink>

          <button
            ref={toggleRef}
            className={`nav-toggle${menuOpen ? ' is-open' : ''}`}
            type="button"
            aria-label={menuOpen ? 'Đóng trình đơn' : 'Mở trình đơn'}
            aria-controls="primary-navigation"
            aria-expanded={menuOpen}
            title={menuOpen ? 'Đóng trình đơn' : 'Mở trình đơn'}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <span className="nav-toggle__bars" aria-hidden="true"><span /><span /><span /></span>
          </button>

          <nav
            id="primary-navigation"
            className={`app-nav${menuOpen ? ' is-open' : ''}`}
            aria-label="Điều hướng chính"
          >
            <NavLink to="/recommendation" onClick={() => setMenuOpen(false)}>Gợi ý charm</NavLink>
            <NavLink to="/catalog" onClick={() => setMenuOpen(false)}>Danh mục</NavLink>
            <NavLink to="/history-quiz" onClick={() => setMenuOpen(false)}>Đố vui</NavLink>
          </nav>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <p>Your Story Your Charm</p>
      </footer>
    </div>
  );
}

export default MainLayout;
