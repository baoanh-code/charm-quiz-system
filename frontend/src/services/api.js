
import { getSupabaseClient } from './supabaseClient.js';

const API_BASE_URL =
  import.meta.env?.VITE_API_BASE_URL || 'http://localhost:8080/api';

export class ApiError extends Error {
  constructor(status, statusText, code, serverMessage) {
    super(serverMessage || `API request failed: ${status}${statusText ? ` ${statusText}` : ''}`);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.serverMessage = serverMessage;
  }
}

export async function request(endpoint, options = {}) {
  const headers = { ...options.headers };
  if (
    options.body != null &&
    !(options.body instanceof FormData) &&
    !Object.keys(headers).some((key) => key.toLowerCase() === 'content-type')
  ) {
    headers['Content-Type'] = 'application/json';
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  if (!response.ok) {
    const contentType = response.headers.get('content-type') || '';
    let errorBody = null;
    if (contentType.includes('application/json')) {
      try {
        errorBody = await response.json();
      } catch {
        errorBody = null;
      }
    }
    throw new ApiError(
      response.status,
      response.statusText,
      errorBody?.code,
      errorBody?.message,
    );
  }

  if (response.status === 204) return null;

  const contentType = response.headers.get('content-type') || '';
  return contentType.includes('application/json') ? response.json() : response.text();
}

export async function managerRequest(endpoint, options = {}) {
  const { accessToken: suppliedAccessToken, ...requestOptions } = options;
  let accessToken = suppliedAccessToken;

  if (!accessToken) {
    const supabase = getSupabaseClient();
    if (!supabase) throw new ApiError(401, 'Manager session unavailable');

    const { data, error } = await supabase.auth.getSession();
    accessToken = data?.session?.access_token;
    if (error || !accessToken) throw new ApiError(401, 'Manager session unavailable');
  }

  return request(endpoint, {
    ...requestOptions,
    headers: {
      ...requestOptions.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  });
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
