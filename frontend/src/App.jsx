
import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import ManagerAuthProvider from './manager/ManagerAuthProvider';
import ManagerHome from './manager/ManagerHome';
import ManagerLogin from './manager/ManagerLogin';
import ManagerQuizzes from './manager/ManagerQuizzes';
import ManagerRules from './manager/ManagerRules';
import RequireManagerAuth from './manager/RequireManagerAuth';
import Catalog from './pages/Catalog';
import HistoryQuiz from './pages/HistoryQuiz';
import Home from './pages/Home';
import Recommendation from './pages/Recommendation';
import RecommendationResults from './pages/RecommendationResults';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<MainLayout />}>
          <Route path="/" element={<Home />} />
          <Route path="/recommendation" element={<Recommendation />} />
          <Route path="/recommendation/results" element={<RecommendationResults />} />
          <Route path="/catalog" element={<Catalog />} />
          <Route path="/history-quiz" element={<HistoryQuiz />} />
        </Route>
        <Route path="/manager" element={<ManagerAuthProvider />}>
          <Route path="login" element={<ManagerLogin />} />
          <Route element={<RequireManagerAuth />}>
            <Route index element={<ManagerHome />} />
            <Route path="rules" element={<ManagerRules />} />
            <Route path="quizzes" element={<ManagerQuizzes />} />
            <Route path="*" element={<Navigate to="/manager" replace />} />
          </Route>
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
