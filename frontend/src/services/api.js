
const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

async function request(endpoint, options = {}) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new Error(`API request failed: ${response.status}`);
  }

  return response.json();
}

export function getHealth() {
  return request('/health');
}

export function getCharms() {
  return request('/charms');
}

export function getRecommendation(payload) {
  return request('/recommendations', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
