/// <reference types="vite/client" />

// Define the type of properties added to the Window object
interface Window {
  // We're using tileManager directly in multiple components
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  tileManager: any;
}
