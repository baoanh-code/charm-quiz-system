import { useEffect, useRef, useState } from 'react';
import { ImagePlus, Trash2, X } from 'lucide-react';

const MAX_IMAGE_SIZE = 5 * 1024 * 1024;
const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'];

function makeDraft(charm) {
  return {
    name: charm?.name ?? '',
    description: charm?.description ?? '',
    category: charm?.category ?? '',
    tags: charm?.tags?.join(', ') ?? '',
    imageUrl: charm?.imageUrl ?? '',
    imageFile: charm?.imageFile ?? null,
    status: charm?.status ?? 'ACTIVE',
    stockStatus: charm?.stockStatus ?? 'IN_STOCK',
  };
}

function CharmEditor({ charm, categories, onSave, onClose }) {
  const dialogRef = useRef(null);
  const readerRef = useRef(null);
  const [draft, setDraft] = useState(() => makeDraft(charm));
  const [imageState, setImageState] = useState('idle');
  const [imageError, setImageError] = useState('');
  const [failedImage, setFailedImage] = useState('');
  const [formError, setFormError] = useState('');

  useEffect(() => {
    const dialog = dialogRef.current;
    dialog.showModal();

    return () => {
      readerRef.current?.abort();
      if (dialog.open) dialog.close();
    };
  }, []);

  function updateDraft(field, value) {
    setDraft((current) => ({ ...current, [field]: value }));
    setFormError('');
  }

  function handleImageFile(event) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;

    if (!IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_SIZE) {
      setImageError('Choose a JPG, PNG, or WebP image under 5 MB.');
      return;
    }

    readerRef.current?.abort();
    const reader = new FileReader();
    readerRef.current = reader;
    setImageState('uploading');
    setImageError('');
    setFailedImage('');

    reader.onload = () => {
      if (readerRef.current !== reader) return;
      setDraft((current) => ({
        ...current,
        imageUrl: String(reader.result),
        imageFile: file,
      }));
      setImageState('ready');
    };
    reader.onerror = () => {
      if (readerRef.current !== reader) return;
      setImageState('idle');
      setImageError('The image could not be opened. Try another file.');
    };
    reader.readAsDataURL(file);
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (imageState === 'uploading') return;

    const name = draft.name.trim();
    const description = draft.description.trim();
    const category = draft.category.trim();
    if (!name || !description || !category) {
      setFormError('Name, description, and category are required.');
      return;
    }
    if (draft.imageUrl && draft.imageUrl === failedImage) {
      setFormError('Choose a working image or remove it.');
      return;
    }
    if (draft.imageUrl && !draft.imageFile) {
      try {
        const imageUrl = new URL(draft.imageUrl);
        if (!['http:', 'https:'].includes(imageUrl.protocol)) throw new Error('Invalid image URL');
      } catch {
        setFormError('Enter an HTTP or HTTPS image URL.');
        return;
      }
    }

    onSave({
      ...draft,
      name,
      description,
      category,
      tags: [...new Set(draft.tags.split(',').map((tag) => tag.trim()).filter(Boolean))],
    });
    onClose();
  }

  const showImage = draft.imageUrl && draft.imageUrl !== failedImage;

  return (
    <dialog ref={dialogRef} className="manager-editor" onCancel={(event) => {
      event.preventDefault();
      onClose();
    }} aria-labelledby="manager-editor-title">
      <div className="manager-editor__header">
        <div>
          <p className="manager-eyebrow">Charm inventory</p>
          <h2 id="manager-editor-title">{charm ? 'Edit charm' : 'Add charm'}</h2>
        </div>
        <button className="manager-icon-button" type="button" title="Close" aria-label="Close" onClick={onClose}>
          <X size={20} aria-hidden="true" />
        </button>
      </div>

      <form className="manager-editor__form" onSubmit={handleSubmit}>
        <div className="manager-field">
          <label htmlFor="charm-name">Name</label>
          <input id="charm-name" value={draft.name} onChange={(event) => updateDraft('name', event.target.value)} autoFocus required />
        </div>
        <div className="manager-field">
          <label htmlFor="charm-category">Category</label>
          <input id="charm-category" list="manager-categories" value={draft.category} onChange={(event) => updateDraft('category', event.target.value)} required />
          <datalist id="manager-categories">
            {categories.map((category) => <option key={category} value={category} />)}
          </datalist>
        </div>
        <div className="manager-field manager-field--wide">
          <label htmlFor="charm-description">Description</label>
          <textarea id="charm-description" rows="3" value={draft.description} onChange={(event) => updateDraft('description', event.target.value)} required />
        </div>
        <div className="manager-field manager-field--wide">
          <label htmlFor="charm-tags">Tags</label>
          <input id="charm-tags" value={draft.tags} onChange={(event) => updateDraft('tags', event.target.value)} placeholder="heritage, gift, gold" />
        </div>

        <div className="manager-image-section manager-field--wide">
          <span className="manager-field__label">Image</span>
          <div className="manager-image-section__body">
            <div className="manager-image-preview" aria-label="Charm image preview">
              {showImage ? (
                <img src={draft.imageUrl} alt="Charm preview" onError={() => {
                  setFailedImage(draft.imageUrl);
                  setImageError('The image could not be displayed.');
                }} />
              ) : <ImagePlus size={32} aria-hidden="true" />}
            </div>
            <div className="manager-image-section__controls">
              <div className="manager-field">
                <label htmlFor="charm-image-url">Image URL</label>
                <input id="charm-image-url" type="url" value={draft.imageUrl.startsWith('data:') ? '' : draft.imageUrl} onChange={(event) => {
                  readerRef.current?.abort();
                  readerRef.current = null;
                  setDraft((current) => ({ ...current, imageUrl: event.target.value, imageFile: null }));
                  setImageState('idle');
                  setImageError('');
                  setFailedImage('');
                }} placeholder="https://example.com/charm.jpg" />
              </div>
              <div className="manager-image-section__actions">
                <label className="button button--secondary manager-file-button">
                  <ImagePlus size={16} aria-hidden="true" />
                  Select image
                  <input type="file" accept="image/jpeg,image/png,image/webp" onChange={handleImageFile} />
                </label>
                {draft.imageUrl ? (
                  <button className="manager-icon-button" type="button" title="Remove image" aria-label="Remove image" onClick={() => {
                    readerRef.current?.abort();
                    readerRef.current = null;
                    setDraft((current) => ({ ...current, imageUrl: '', imageFile: null }));
                    setImageState('idle');
                    setImageError('');
                    setFailedImage('');
                  }}>
                    <Trash2 size={18} aria-hidden="true" />
                  </button>
                ) : null}
              </div>
              {imageState === 'uploading' ? <p className="manager-field__hint" role="status">Preparing image...</p> : null}
              {imageError ? <p className="manager-form-error" role="alert">{imageError}</p> : null}
            </div>
          </div>
        </div>

        <div className="manager-editor__switches manager-field--wide">
          <label className="manager-switch">
            <input type="checkbox" checked={draft.status === 'ACTIVE'} onChange={(event) => updateDraft('status', event.target.checked ? 'ACTIVE' : 'INACTIVE')} />
            <span>Active</span>
          </label>
          <label className="manager-switch">
            <input type="checkbox" checked={draft.stockStatus === 'IN_STOCK'} onChange={(event) => updateDraft('stockStatus', event.target.checked ? 'IN_STOCK' : 'OUT_OF_STOCK')} />
            <span>In stock</span>
          </label>
        </div>

        {formError ? <p className="manager-form-error manager-field--wide" role="alert">{formError}</p> : null}
        <div className="manager-editor__footer manager-field--wide">
          <button className="button button--secondary" type="button" onClick={onClose}>Cancel</button>
          <button className="button button--primary" type="submit" disabled={imageState === 'uploading'}>Save preview</button>
        </div>
      </form>
    </dialog>
  );
}

export default CharmEditor;
