
import { NavLink, Outlet } from 'react-router-dom';

function MainLayout() {
  return (
    <div className="app-layout">
      <header className="app-header">
        <NavLink className="app-brand" to="/" aria-label="Charm Quiz System home">
          <span className="brand-mark">CQ</span>
          <span>Charm Quiz System</span>
        </NavLink>

        <nav className="app-nav" aria-label="Primary navigation">
          <NavLink to="/recommendation">Recommend</NavLink>
          <NavLink to="/catalog">Catalog</NavLink>
          <NavLink to="/history-quiz">Quiz</NavLink>
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
