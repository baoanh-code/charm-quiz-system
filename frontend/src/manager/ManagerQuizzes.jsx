import { useEffect, useRef, useState } from 'react';
import { FileJson, Upload, X } from 'lucide-react';
import { useOutletContext } from 'react-router-dom';
import {
  importManagerQuiz,
  inspectQuizFile,
  loadManagerQuizzes,
  selectImportedQuiz,
  updateManagerQuizStatus,
  validateManagerQuiz,
} from '../services/managerQuizService';

function QuestionList({ questions, issues = [] }) {
  return (
    <ol className="manager-question-list">
      {questions.map((question, index) => {
        const prefix = `questions[${index}]`;
        const questionIssues = issues.filter((issue) => issue.path === prefix || issue.path.startsWith(`${prefix}.`));
        const options = Array.isArray(question?.options) ? question.options : [];
        return (
          <li key={question?.id ?? index} className={questionIssues.length ? 'manager-question manager-question--invalid' : 'manager-question'}>
            <div className="manager-question__heading">
              <span>Question {index + 1}</span>
              {questionIssues.length ? <strong>Needs attention</strong> : null}
            </div>
            <h3>{typeof question?.prompt === 'string' && question.prompt.trim() ? question.prompt : 'Question text missing'}</h3>
            <ol className="manager-question__options" type="A">
              {options.map((option, optionIndex) => (
                <li key={optionIndex} className={optionIndex === question?.correctIndex ? 'manager-question__correct' : ''}>
                  <span>{typeof option === 'string' && option.trim() ? option : 'Option text missing'}</span>
                  {optionIndex === question?.correctIndex ? <span className="manager-question__answer">Correct</span> : null}
                </li>
              ))}
            </ol>
            {question?.explanation ? <p className="manager-question__explanation">{question.explanation}</p> : null}
            {questionIssues.length ? (
              <ul className="manager-question__issues">
                {questionIssues.map((issue) => <li key={issue.path}><code>{issue.path}</code>: {issue.message}</li>)}
              </ul>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

function ManagerQuizzes() {
  const { user } = useOutletContext();
  const fileRef = useRef(null);
  const readId = useRef(0);
  const savingRef = useRef(false);
  const [quizzes, setQuizzes] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [retryKey, setRetryKey] = useState(0);
  const [fileName, setFileName] = useState('');
  const [imported, setImported] = useState(null);
  const [reading, setReading] = useState(false);
  const [validating, setValidating] = useState(false);
  const [saving, setSaving] = useState(false);
  const [importError, setImportError] = useState('');
  const [actionError, setActionError] = useState('');
  const [pendingId, setPendingId] = useState(null);
  const [notice, setNotice] = useState('');

  useEffect(() => {
    let active = true;
    loadManagerQuizzes().then((rows) => {
      if (!active) return;
      setQuizzes(rows);
      setSelectedId((current) => rows.some((quiz) => quiz.id === current)
        ? current
        : rows[0]?.id ?? null);
      setLoadError('');
    }).catch(() => {
      if (active) setLoadError('Could not load quiz data. Please try again.');
    });
    return () => { active = false; };
  }, [retryKey]);

  const selectedQuiz = quizzes?.find((quiz) => quiz.id === selectedId);

  function cancelImport() {
    readId.current += 1;
    if (fileRef.current) fileRef.current.value = '';
    setFileName('');
    setImported(null);
    setReading(false);
    setValidating(false);
    setImportError('');
  }

  async function handleFile(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    const currentRead = ++readId.current;
    setFileName(file.name);
    setImported(null);
    setReading(true);
    setValidating(false);
    setImportError('');
    setNotice('');
    try {
      const result = await inspectQuizFile(file);
      if (readId.current !== currentRead) return;
      setImported({ ...result, backendValidated: false });
      setReading(false);

      if (!result.issues.length) {
        setValidating(true);
        await validateManagerQuiz(result);
        if (readId.current === currentRead) {
          setImported({ ...result, backendValidated: true });
        }
      }
    } catch {
      if (readId.current === currentRead) {
        setImportError('The quiz could not be validated. Check your session and try again.');
      }
    } finally {
      if (readId.current === currentRead) {
        setReading(false);
        setValidating(false);
      }
    }
  }

  async function handleSave() {
    if (
      savingRef.current ||
      !imported?.backendValidated ||
      imported.issues.length ||
      reading ||
      validating
    ) return;
    savingRef.current = true;
    setSaving(true);
    setImportError('');
    try {
      const quiz = await importManagerQuiz(imported);
      const next = selectImportedQuiz(quizzes ?? [], quiz);
      setQuizzes(next.quizzes);
      setSelectedId(next.selectedId);
      cancelImport();
      setNotice(`${quiz.title} imported successfully as an inactive quiz.`);
    } catch {
      setImportError('Could not import the quiz. Check your session and try again.');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  async function handleActivate(quiz) {
    if (pendingId) return;
    setPendingId(quiz.id);
    setActionError('');
    setNotice('');
    try {
      await updateManagerQuizStatus(quiz.id, 'ACTIVE');
      const rows = await loadManagerQuizzes();
      setQuizzes(rows);
      setSelectedId(quiz.id);
      setNotice(`${quiz.title} is now the active customer quiz.`);
    } catch {
      setActionError('Could not activate this quiz. Please try again.');
    } finally {
      setPendingId(null);
    }
  }

  return (
    <section className="manager-workspace" aria-labelledby="manager-quizzes-title">
      <header className="manager-workspace__heading">
        <div>
          <p className="manager-eyebrow">History quiz</p>
          <h1 id="manager-quizzes-title">Quiz data</h1>
          <p>Signed in as {user.email}</p>
        </div>
      </header>

      <p className="manager-preview-note">Quiz imports are checked locally and by the backend before they are saved.</p>
      {notice ? <p className="manager-save-notice" role="status">{notice}</p> : null}

      <section className="manager-import" aria-labelledby="manager-import-title">
        <div className="manager-list-heading"><h2 id="manager-import-title">Import JSON</h2><span>Local + backend validation</span></div>
        <div className="manager-import__controls">
          <label className="button button--secondary manager-file-button">
            <Upload size={17} aria-hidden="true" /> Select JSON
            <input ref={fileRef} type="file" accept=".json,application/json" onChange={handleFile} disabled={saving} aria-label="Select quiz JSON file" />
          </label>
          {fileName ? <span className="manager-import__filename"><FileJson size={17} aria-hidden="true" />{fileName}</span> : null}
          {fileName && !saving ? <button className="manager-icon-button" type="button" title="Cancel import" aria-label="Cancel import" onClick={cancelImport}><X size={18} aria-hidden="true" /></button> : null}
        </div>

        {reading ? <p className="manager-import__message" role="status" aria-busy="true">Reading JSON file...</p> : null}
        {validating ? <p className="manager-import__message" role="status" aria-busy="true">Validating quiz with the backend...</p> : null}
        {importError ? <p className="manager-form-error" role="alert">{importError}</p> : null}
        {imported ? (
          <div className="manager-import__result">
            {imported.issues.length ? (
              <div className="manager-import__invalid" role="alert">
                <strong>{imported.issues.length} preview {imported.issues.length === 1 ? 'issue' : 'issues'} found</strong>
                <ul>{imported.issues.map((issue, index) => <li key={`${issue.path}-${index}`}><code>{issue.path}</code>: {issue.message}</li>)}</ul>
              </div>
            ) : imported.backendValidated ? (
              <p className="manager-save-notice" role="status">Validation passed · {imported.questions.length} questions ready</p>
            ) : null}

            {imported.questions.length ? (
              <div className="manager-import__preview">
                <h3>{imported.title}</h3>
                <QuestionList questions={imported.questions} issues={imported.issues} />
              </div>
            ) : null}
            <div className="manager-import__actions">
              <button className="button button--secondary" type="button" onClick={cancelImport} disabled={saving}>Cancel</button>
              <button className="button button--primary" type="button" onClick={handleSave} disabled={saving || validating || !imported.backendValidated || imported.issues.length > 0}>
                {saving ? 'Importing...' : 'Import quiz'}
              </button>
            </div>
          </div>
        ) : null}
      </section>

      <section className="manager-quiz-library" aria-labelledby="manager-library-title">
        <div className="manager-list-heading"><h2 id="manager-library-title">Quiz library</h2><span role="status">{quizzes ? `${quizzes.length} quizzes` : 'Loading quizzes...'}</span></div>
        {loadError ? <div className="manager-list-message" role="alert"><p>{loadError}</p><button className="button button--secondary" type="button" onClick={() => { setLoadError(''); setRetryKey((key) => key + 1); }}>Try again</button></div> : null}
        {!quizzes && !loadError ? <div className="manager-list-message" role="status" aria-busy="true">Loading quiz data...</div> : null}
        {quizzes?.length === 0 ? <div className="manager-list-message"><h3>No quizzes yet</h3></div> : null}
        {quizzes?.length ? (
          <div className="manager-quiz-library__layout">
            <nav className="manager-quiz-library__list" aria-label="Quiz library">
              {quizzes.map((quiz) => <button key={quiz.id} type="button" className={quiz.id === selectedId ? 'manager-quiz-library__item manager-quiz-library__item--selected' : 'manager-quiz-library__item'} onClick={() => setSelectedId(quiz.id)} aria-current={quiz.id === selectedId ? 'true' : undefined}>
                <span>{quiz.title}</span><small>{quiz.questions.length} questions · {quiz.status}</small>
              </button>)}
            </nav>
            {selectedQuiz ? (
              <div className="manager-quiz-library__detail">
                <div className="manager-list-heading manager-quiz-detail__heading">
                  <div>
                    <h2>{selectedQuiz.title}</h2>
                    <span className={`manager-state manager-state--${selectedQuiz.status.toLowerCase()}`}>{selectedQuiz.status}</span>
                  </div>
                  {selectedQuiz.status === 'INACTIVE' ? (
                    <button className="button button--secondary" type="button" disabled={pendingId !== null} onClick={() => handleActivate(selectedQuiz)}>
                      {pendingId === selectedQuiz.id ? 'Activating...' : 'Activate quiz'}
                    </button>
                  ) : null}
                </div>
                {selectedQuiz.description ? <p className="manager-quiz-detail__description">{selectedQuiz.description}</p> : null}
                {actionError ? <p className="manager-form-error" role="alert">{actionError}</p> : null}
                <QuestionList questions={selectedQuiz.questions} />
              </div>
            ) : null}
          </div>
        ) : null}
      </section>
    </section>
  );
}

export default ManagerQuizzes;
