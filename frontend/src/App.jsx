
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import Catalog from './pages/Catalog';
import FeaturePlaceholder from './pages/FeaturePlaceholder';
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
          <Route
            path="/history-quiz"
            element={
              <FeaturePlaceholder
                title="Vietnam History Quiz"
                description="A lightweight quiz experience will let customers play and learn about Vietnam history."
              />
            }
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
