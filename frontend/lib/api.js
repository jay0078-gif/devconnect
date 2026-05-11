const BASE_URL = 'http://localhost:8080/api';

function getToken() {
  return localStorage.getItem('token');
}

export async function apiFetch(path, options = {}) {
  const token = getToken();
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!res.ok) throw new Error(await res.text());
  return res.json();
}

// Auth
export const login = (data) => apiFetch('/auth/login', { method: 'POST', body: JSON.stringify(data) });
export const register = (data) => apiFetch('/auth/register', { method: 'POST', body: JSON.stringify(data) });

// Users
export const getMe = () => apiFetch('/users/me');
export const updateProfile = (data) => apiFetch('/users/me', { method: 'PUT', body: JSON.stringify(data) });

// Posts
export const getGlobalFeed = (page = 0) => apiFetch(`/posts/feed/global?page=${page}&size=10`);
export const getMyFeed = (page = 0) => apiFetch(`/posts/feed/me?page=${page}&size=10`);
export const createPost = (data) => apiFetch('/posts', { method: 'POST', body: JSON.stringify(data) });
export const getPost = (id) => apiFetch(`/posts/${id}`);
export const likePost = (id) => apiFetch(`/posts/${id}/like`, { method: 'POST' });

// Trending
export const getTrending = () => apiFetch('/trending');