import { createContext, useContext } from 'react';

export const ManagerAuthContext = createContext(null);

export function useManagerAuth() {
  const context = useContext(ManagerAuthContext);
  if (!context) throw new Error('Manager auth context is unavailable');
  return context;
}
