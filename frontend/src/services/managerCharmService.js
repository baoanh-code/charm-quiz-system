import { managerRequest } from './api.js';

const MANAGER_CHARMS_ENDPOINT = '/manager/charms';
const CHARM_STATUSES = ['ACTIVE', 'INACTIVE'];
const STOCK_STATUSES = ['IN_STOCK', 'OUT_OF_STOCK'];

export class ManagerCharmWorkflowError extends Error {
  constructor(action, cause) {
    super(cause?.message || 'Manager charm operation failed', { cause });
    this.name = 'ManagerCharmWorkflowError';
    this.action = action;
    this.status = cause?.status;
    this.code = cause?.code;
  }
}

function nullableString(value) {
  return value == null || typeof value === 'string';
}

export function normalizeManagerCharm(charm) {
  if (
    !charm || charm.id == null ||
    typeof charm.name !== 'string' || !charm.name.trim() ||
    !nullableString(charm.description) ||
    typeof charm.imageUrl !== 'string' || !charm.imageUrl.trim() ||
    !nullableString(charm.category) ||
    !Array.isArray(charm.tags) || charm.tags.some((tag) => typeof tag !== 'string') ||
    !CHARM_STATUSES.includes(charm.status) || !STOCK_STATUSES.includes(charm.stockStatus)
  ) {
    throw new Error('Unexpected manager charm response');
  }
  return {
    id: String(charm.id), name: charm.name, description: charm.description ?? null,
    imageUrl: charm.imageUrl, category: charm.category ?? null, tags: [...charm.tags],
    status: charm.status, stockStatus: charm.stockStatus,
  };
}

function normalizeCharmList(response) {
  if (!Array.isArray(response)) throw new Error('Unexpected manager charm list response');
  return response.map(normalizeManagerCharm);
}

function charmPayload(charm) {
  return {
    name: charm.name, description: charm.description || null, imageUrl: charm.imageUrl,
    category: charm.category || null, tags: charm.tags ?? [], status: charm.status,
    stockStatus: charm.stockStatus,
  };
}

export function filterManagerCharms(charms, { search, category, status, stockStatus }) {
  const query = search.trim().toLocaleLowerCase();
  return charms.filter((charm) =>
    (!query || charm.name.toLocaleLowerCase().includes(query)) &&
    (category === 'all' || charm.category === category) &&
    (status === 'all' || charm.status === status) &&
    (stockStatus === 'all' || charm.stockStatus === stockStatus),
  );
}

export async function loadManagerCharms(options = {}) {
  return normalizeCharmList(await managerRequest(MANAGER_CHARMS_ENDPOINT, options));
}

export async function createManagerCharm(charm, options = {}) {
  return normalizeManagerCharm(await managerRequest(MANAGER_CHARMS_ENDPOINT, {
    ...options, method: 'POST', body: JSON.stringify(charmPayload(charm)),
  }));
}

export async function updateManagerCharm(id, charm, options = {}) {
  return normalizeManagerCharm(await managerRequest(
    `${MANAGER_CHARMS_ENDPOINT}/${encodeURIComponent(id)}`,
    { ...options, method: 'PUT', body: JSON.stringify(charmPayload(charm)) },
  ));
}

export async function updateManagerCharmStatus(id, status, options = {}) {
  if (!CHARM_STATUSES.includes(status)) throw new Error('Invalid manager charm status');
  return normalizeManagerCharm(await managerRequest(
    `${MANAGER_CHARMS_ENDPOINT}/${encodeURIComponent(id)}/status`,
    { ...options, method: 'PATCH', body: JSON.stringify({ status }) },
  ));
}

export async function updateManagerCharmStockStatus(id, stockStatus, options = {}) {
  if (!STOCK_STATUSES.includes(stockStatus)) throw new Error('Invalid manager charm stock status');
  return normalizeManagerCharm(await managerRequest(
    `${MANAGER_CHARMS_ENDPOINT}/${encodeURIComponent(id)}/stock-status`,
    { ...options, method: 'PATCH', body: JSON.stringify({ stockStatus }) },
  ));
}

export async function uploadManagerCharmImage(file, options = {}) {
  const formData = new FormData();
  formData.append('file', file);
  const response = await managerRequest('/manager/charm-images', {
    ...options, method: 'POST', body: formData,
  });
  if (!response || typeof response.imageUrl !== 'string' || !response.imageUrl.trim()) {
    throw new Error('Unexpected manager charm image response');
  }
  return response.imageUrl;
}

export async function saveManagerCharm({ id, draft }, options = {}) {
  let imageUrl = draft.imageUrl;
  if (draft.imageFile) {
    try {
      imageUrl = await uploadManagerCharmImage(draft.imageFile, options);
    } catch (error) {
      throw new ManagerCharmWorkflowError('upload', error);
    }
  }

  const payload = { ...draft, imageUrl };
  try {
    return id
      ? await updateManagerCharm(id, payload, options)
      : await createManagerCharm(payload, options);
  } catch (error) {
    throw new ManagerCharmWorkflowError(id ? 'update' : 'create', error);
  }
}

export function managerCharmErrorMessage(error) {
  if (error?.status === 401) return 'Your Manager session has expired. Sign in again.';
  if (error?.status === 403) return 'Your account is not allowed to perform this Manager action.';
  if (error?.status === 413 || error?.code === 'IMAGE_TOO_LARGE') {
    return 'The image is too large. Choose an image under 5 MB.';
  }
  if (error?.action === 'upload' && error?.status === 502) {
    return 'Supabase Storage rejected the image upload. Try again or contact an administrator.';
  }
  if (error?.action === 'upload') return 'Could not upload the image. Please try again.';
  if (error?.code === 'VALIDATION_ERROR') return 'The charm details are invalid. Check the fields and try again.';
  if (error?.status === 409) return 'This change conflicts with existing charm data.';
  return error?.message || 'Could not save the charm. Try again.';
}

export function selectManagerCharmImage(draft, file, previewUrl) {
  return { ...draft, imageUrl: previewUrl, imageFile: file };
}

export function clearManagerCharmImage(draft) {
  return { ...draft, imageUrl: '', imageFile: null };
}

export async function deleteManagerCharm(id, options = {}) {
  await managerRequest(`${MANAGER_CHARMS_ENDPOINT}/${encodeURIComponent(id)}`, {
    ...options, method: 'DELETE',
  });
}

export async function deleteManagerCharmAndReload(id, options = {}) {
  await deleteManagerCharm(id, options);
  return loadManagerCharms(options);
}
