
const API_BASE_URL =
  import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080/api';

export class ApiError extends Error {
  constructor(status, statusText) {
    super(`API request failed: ${status}${statusText ? ` ${statusText}` : ''}`);
    this.name = 'ApiError';
    this.status = status;
  }
}

export async function request(endpoint, options = {}) {
  const headers = { ...options.headers };
  if (options.body != null && !Object.keys(headers).some((key) => key.toLowerCase() === 'content-type')) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    throw new ApiError(response.status, response.statusText);
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

export function getHealth() {
  return request('/health');
}

export function getCharms(options = {}) {
  return request('/charms', options);
}

export function getRecommendation(payload, options = {}) {
  return request('/recommendations', {
    ...options,
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
