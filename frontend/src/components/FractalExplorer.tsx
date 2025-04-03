import { useState, useEffect, useRef, useCallback } from 'react'
import { FractalCanvas } from './FractalCanvas'
import { ControlPanel } from './ControlPanel'
import { TileManager } from './TileManager'
import { ViewState } from '../state/ViewState'
import { RenderState } from '../state/RenderState'
import './FractalExplorer.css'

// TileManager is defined in global.d.ts

/**
 * API URL for rendering requests
 */
const API_URL = import.meta.env.PROD
  ? '/api/fractal'
  : 'http://localhost:8080/api/fractal'

// Log the API URL for debugging
console.log("Using API URL:", API_URL);

/**
 * WebSocket URL for real-time updates
 * Using SockJS endpoints
 */
const WS_URL = import.meta.env.PROD
  ? `ws://${window.location.host}/ws/fractal/websocket`
  : 'ws://localhost:8080/ws/fractal/websocket'

// Log the WebSocket URL for debugging
console.log("Using WebSocket URL:", WS_URL);

/**
 * Main FractalExplorer component
 */
export function FractalExplorer() {
  // Application state
  const [viewState] = useState(new ViewState())
  // Use a single render state instance
  const [renderState] = useState(() => new RenderState())
  // Force a re-render when state updates
  const [, setForceUpdate] = useState(0)
  // Track if initial render has occurred to prevent re-renders on reconnection
  const initialRenderRef = useRef(false)
  // Track websocket reconnection attempts
  const reconnectingRef = useRef(false)
  const [tileManager] = useState(() => {
    const manager = new TileManager();
    // Make the tile manager globally accessible for direct access
    if (typeof window !== 'undefined') {
      window.tileManager = manager;
    }
    return manager;
  })
  const [webSocket, setWebSocket] = useState<WebSocket | null>(null)
  
  // Setup WebSocket connection
  useEffect(() => {
    setupWebSocket()
    
    // Force rendering progress indicator to be visible initially
    renderState.isLoading = true
    setForceUpdate(n => n + 1)
    
    return () => {
      // Cleanup WebSocket on component unmount
      if (webSocket) {
        webSocket.close()
      }
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])
  
  // Trigger initial render when component mounts and WebSocket connects
  useEffect(() => {
    // Only trigger initial render if websocket is connected AND 
    // we haven't rendered before AND we're not in the middle of reconnecting
    if (webSocket && webSocket.readyState === WebSocket.OPEN && 
        !initialRenderRef.current && !reconnectingRef.current) {
      console.log("🚀 Triggering initial render on page load")
      initialRenderRef.current = true;
      requestRender()
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [webSocket])
  
  // Setup the WebSocket connection
  const setupWebSocket = () => {
    console.log(`Attempting to connect to WebSocket at: ${WS_URL}`)
    
    // Set reconnecting flag to prevent unwanted render triggering
    reconnectingRef.current = true;
    
    try {
      const ws = new WebSocket(WS_URL)
      
      ws.onopen = () => {
        console.log('WebSocket connection established successfully')
        setWebSocket(ws)
        
        // If we already have a job ID, subscribe to updates
        if (renderState.currentJobId) {
          console.log(`Reconnected and subscribing to existing job ID: ${renderState.currentJobId}`)
          ws.send(JSON.stringify({
            type: 'subscribe',
            jobId: renderState.currentJobId
          }))
          
          // Clear reconnecting flag after successful resubscription
          reconnectingRef.current = false;
        } else {
          // No active job, clear reconnecting flag
          reconnectingRef.current = false;
        }
      }
      
      ws.onmessage = (event) => {
        try {
          console.log('WebSocket message received:', event.data)
          const message = JSON.parse(event.data)
          
          // Validate that the message belongs to the current job
          if (message.jobId && renderState.currentJobId && 
              message.jobId !== renderState.currentJobId && 
              message.type !== 'error') {
            console.warn(`Ignoring message from job ${message.jobId} - current job is ${renderState.currentJobId}`);
            return;
          }
          
          // Handle different message types
          switch (message.type) {
            case 'tile':
              handleTileUpdate(message)
              break
              
            case 'progress':
              handleProgressUpdate(message)
              break
              
            case 'error':
              handleErrorMessage(message)
              break
              
            default:
              console.warn('Unknown message type:', message.type)
          }
        } catch (error) {
          console.error('Error processing WebSocket message:', error)
        }
      }
      
      ws.onclose = (event) => {
        console.log(`WebSocket connection closed. Code: ${event.code}, Reason: ${event.reason}`)
        setWebSocket(null)
        
        // Set reconnecting flag
        reconnectingRef.current = true;
        
        // Try to reconnect after a delay
        console.log('Will attempt to reconnect in 3 seconds...')
        setTimeout(() => {
          setupWebSocket()
        }, 3000)
      }
      
      ws.onerror = (error) => {
        console.error('WebSocket error occurred:', error)
      }
    } catch (error) {
      console.error('Error creating WebSocket connection:', error)
      // Clear reconnecting flag on error
      reconnectingRef.current = false;
    }
  }
  
  // Debounced canvas redraw - use a ref to avoid recreation on each render
  const drawDebounceTimeoutRef = useRef<number | null>(null);
  const canvasUpdatePendingRef = useRef(false);
  
  // Debounced canvas redraw function to reduce excessive redraws
  const debouncedCanvasRedraw = useCallback((immediate: boolean = false) => {
    if (drawDebounceTimeoutRef.current !== null) {
      window.clearTimeout(drawDebounceTimeoutRef.current);
      drawDebounceTimeoutRef.current = null;
    }
    
    // For immediate redraws, skip the debouncing
    if (immediate) {
      const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
      if (!canvas) return;
      
      const ctx = canvas.getContext('2d');
      if (!ctx) return;
      
      // Make sure the preview canvas is also ready
      if (immediate && document.querySelector('.preview-canvas')) {
        const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
        previewCanvas.style.display = 'block';
      }
      
      tileManager.drawAllTiles(ctx);
      canvasUpdatePendingRef.current = false;
      return;
    }
    
    // Mark that we have a pending update
    canvasUpdatePendingRef.current = true;
    
    // Use a longer debounce time for better batching
    drawDebounceTimeoutRef.current = window.setTimeout(() => {
      // Only redraw if an update is actually pending
      if (canvasUpdatePendingRef.current) {
        const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
        if (!canvas) return;
        
        const ctx = canvas.getContext('2d');
        if (!ctx) return;
        
        // Ensure we're using fixed canvas dimensions to align tiles correctly
        // Reset the canvas dimensions if they've been changed by browser resizing
        if (canvas.width !== 800 || canvas.height !== 600) {
          canvas.width = 800;
          canvas.height = 600;
          
          const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
          if (previewCanvas) {
            previewCanvas.width = 800;
            previewCanvas.height = 600;
          }
        }
        
        tileManager.drawAllTiles(ctx);
        console.log("Debounced canvas redraw completed");
        canvasUpdatePendingRef.current = false;
      }
      drawDebounceTimeoutRef.current = null;
    }, 100); // 100ms debounce for better batching
  }, [tileManager]);
  
  // Handle tile update message from WebSocket
  const handleTileUpdate = useCallback((message: {
    jobId: string;
    tileId: string;
    x: number;
    y: number;
    width: number;
    height: number;
    imageDataBase64: string;
    type: string;
  }) => {
    // Skip processing if this tile is from a different job
    if (message.jobId !== renderState.currentJobId) {
      console.warn(`Ignoring tile from job ${message.jobId} - current job is ${renderState.currentJobId}`);
      return;
    }
    
    // Check if this is a preview tile
    const isPreviewTile = message.tileId === "preview" || message.tileId.includes("preview");
    
    // For preview tiles, ensure the preview canvas is visible
    if (isPreviewTile) {
      const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
      if (previewCanvas) {
        // Make preview canvas visible and ensure it fills the container
        previewCanvas.style.display = 'block';
        previewCanvas.style.opacity = '1';
        previewCanvas.style.zIndex = '10';
        previewCanvas.style.visibility = 'visible';
        
        // Set explicit dimensions for preview canvas
        if (previewCanvas.width !== 800 || previewCanvas.height !== 600) {
          previewCanvas.width = 800;
          previewCanvas.height = 600;
        }
        
        console.log("Preview tile received - making preview canvas visible");
      }
    }
    
    // Always use the main canvas for processing all tiles
    const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
    if (!canvas) {
      console.error('Canvas element not found when handling tile update');
      return;
    }
    
    const ctx = canvas.getContext('2d');
    if (!ctx) {
      console.error('Could not get 2D context from canvas');
      return;
    }
    
    // Log tile info for debugging
    console.log(`Received tile: ${message.tileId} at x:${message.x}, y:${message.y}, size:${message.width}x${message.height}`);
    
    // Process the tile and get image data and position
    const tileData = tileManager.addTile(message);
    
    // If valid tile data was returned, schedule a redraw
    if (tileData) {
      // Use immediate redraw for preview tiles, debounced for others
      debouncedCanvasRedraw(isPreviewTile);
    } else {
      console.warn(`No tile data returned for tile ${message.tileId}`);
    }
  }, [renderState, tileManager, debouncedCanvasRedraw])
  
  // Handle progress update message from WebSocket
  const handleProgressUpdate = useCallback((message: {
    progress: number;
    completedTiles: number;
    totalTiles: number;
    elapsedTimeMs: number;
    jobId: string;
    type: string;
  }) => {
    const { progress, completedTiles, totalTiles, elapsedTimeMs, jobId } = message
    
    // Skip processing if this progress update is from a different job
    if (jobId !== renderState.currentJobId) {
      console.warn(`Ignoring progress update from job ${jobId} - current job is ${renderState.currentJobId}`);
      return;
    }
    
    // Update the render state
    renderState.updateProgress(progress, completedTiles, totalTiles, elapsedTimeMs);
    
    // Make sure isLoading stays true until we're done
    if (progress < 99) {
      renderState.isLoading = true;
      console.log(`Progress: ${progress}%, ${completedTiles}/${totalTiles} tiles`);
    }
    
    // Force a React component re-render to show updated progress
    setForceUpdate(n => n + 1);
    
    // Use debounced redraw for progress updates to avoid frequent canvas redraws
    // Only redraw on significant progress changes (every ~10%)
    if (progress % 10 === 0 || progress === 100 || progress === 99) {
      debouncedCanvasRedraw();
    }
    
    // If progress is 100% or very close (to handle rounding issues), mark render as complete
    if (progress === 100 || (completedTiles && totalTiles && completedTiles >= totalTiles) || progress >= 99) {
      console.log(`Completing render: progress=${progress}%, completedTiles=${completedTiles}, totalTiles=${totalTiles}`)
      
      // Mark render as complete
      renderState.completeRender();
      
      // Force a component update
      setForceUpdate(n => n + 1);
      
      // Add a class to the body to indicate render is complete
      document.body.classList.add('render-complete');
      
      console.log("Render completed - setting loading state to false");
      
      // Immediately hide the preview and show the high-res tiles
      const hidePreview = () => {
        console.log(`HIDING PREVIEW CANVAS NOW - Render is marked as complete`);
        const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
        
        if (previewCanvas) {
          // Completely remove the preview canvas from view using every technique possible
          previewCanvas.style.display = 'none';
          previewCanvas.style.opacity = '0';
          previewCanvas.style.zIndex = '-1';
          previewCanvas.style.visibility = 'hidden';
          
          // Force the main canvas to be visible and on top
          const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement;
          if (canvas) {
            canvas.style.zIndex = '20'; // Much higher z-index to ensure it's on top
            canvas.style.opacity = '1';
            canvas.style.visibility = 'visible';
            
            // Force redraw multiple times to ensure tiles are displayed
            const ctx = canvas.getContext('2d');
            if (ctx) {
              // Clear the canvas first
              ctx.clearRect(0, 0, canvas.width, canvas.height);
              tileManager.drawAllTiles(ctx);
              
              // Schedule additional redraws to catch any late tiles
              for (const delay of [100, 300, 800]) {
                setTimeout(() => {
                  if (ctx) {
                    tileManager.drawAllTiles(ctx);
                    console.log(`Redrawing after ${delay}ms - ${tileManager.getRenderedTileCount()} tiles (${tileManager.getHighResTilesCount()} high-res)`);
                  }
                }, delay);
              }
            }
          }
        }
      };
      
      // Execute immediately
      hidePreview();
      
      // Also execute after a short delay as a backup
      setTimeout(hidePreview, 500);
      
      // Do an immediate final refresh to ensure all tiles are properly rendered
      debouncedCanvasRedraw(true); // Use immediate mode
      
      // Then do one more after a short delay to catch any late-arriving tiles
      setTimeout(() => {
        const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement
        if (canvas) {
          const ctx = canvas.getContext('2d')
          if (ctx && tileManager) {
            tileManager.drawAllTiles(ctx)
          }
        }
      }, 300);
    }
  }, [renderState, tileManager, debouncedCanvasRedraw, setForceUpdate])
  
  // Handle error message from WebSocket
  const handleErrorMessage = useCallback((message: {
    message: string;
    jobId: string;
    type: string;
  }) => {
    renderState.setError(message.message)
    setForceUpdate(n => n + 1)
    
    // Hide the preview canvas if there's an error
    const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
    if (previewCanvas) {
      previewCanvas.style.display = 'none';
    }
  }, [renderState, setForceUpdate])
  
  // Send a render request to the API
  const requestRender = useCallback(async () => {
    // Don't start a new render if reconnecting or already rendering
    if (reconnectingRef.current) {
      console.log("Skipping render request during WebSocket reconnection");
      return;
    }
    
    // Always allow new renders without confirmation popup
    if (renderState.isLoading && renderState.currentJobId) {
      console.log("Starting a new render while another render is in progress");
    }
    
    try {
      // Get the canvas dimensions
      const canvas = document.querySelector('.fractal-canvas') as HTMLCanvasElement
      if (!canvas) return
      
      // Prepare render parameters
      const params = viewState.getRenderParams(canvas.width, canvas.height)
      
      console.log("Sending render request with params:", params)
      
      // Start a new render job with empty ID (will be filled in when response arrives)
      renderState.startRender('');
      
      // Remove the render-complete class when starting a new render
      document.body.classList.remove('render-complete');
      
      // Clear existing tiles when starting a new render
      // Keep preview tiles if this is a zoom or pan operation
      const isZoomOrPan = renderState.lastOperation === 'zoom' || renderState.lastOperation === 'pan';
      tileManager.clearCache(isZoomOrPan)
      
      // Make sure preview canvas is visible for new render
      const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
      if (previewCanvas) {
        console.log("Setting preview canvas to visible for new render");
        previewCanvas.style.display = 'block';
        previewCanvas.style.opacity = '1';
        previewCanvas.style.zIndex = '10';
        previewCanvas.style.visibility = 'visible';
        
        // Ensure preview canvas has correct dimensions
        if (previewCanvas.width !== 800 || previewCanvas.height !== 600) {
          previewCanvas.width = 800;
          previewCanvas.height = 600;
        }
      }
      
      // Force component update to show loading state
      setForceUpdate(n => n + 1);
      
      // Show loading state on canvas
      const ctx = canvas.getContext('2d')
      if (ctx) {
        ctx.fillStyle = 'black'
        ctx.fillRect(0, 0, canvas.width, canvas.height)
        
        // Draw a "Loading..." text in the center
        ctx.font = '24px Arial'
        ctx.fillStyle = 'white'
        ctx.textAlign = 'center'
        ctx.fillText('Rendering...', canvas.width / 2, canvas.height / 2)
      }
      
      // Send the request to the API
      const response = await fetch(`${API_URL}/render`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(params)
      })
      
      if (!response.ok) {
        const errorText = await response.text()
        console.error("Server error response:", errorText)
        throw new Error(`Server returned ${response.status}: ${response.statusText}`)
      }
      
      // Get the job ID from the response
      const data = await response.json()
      const jobId = data.jobId
      
      console.log("Received job ID:", jobId)
      
      // Update both view and render state with the job ID
      viewState.currentJobId = jobId
      renderState.currentJobId = jobId;
      
      // Force a re-render to update UI
      setForceUpdate(n => n + 1);
      
      // Subscribe to updates for this job via WebSocket
      if (webSocket && webSocket.readyState === WebSocket.OPEN) {
        console.log("Subscribing to job updates for:", jobId)
        webSocket.send(JSON.stringify({
          type: 'subscribe',
          jobId
        }))
      }
    } catch (error) {
      console.error('Error requesting render:', error)
      renderState.setError(error instanceof Error ? error.message : 'Unknown error')
      setForceUpdate(n => n + 1);
    }
  }, [webSocket, renderState, viewState, tileManager, reconnectingRef, setForceUpdate])
  
  // Handle zoom events
  const handleZoom = useCallback((centerX: number, centerY: number, zoomFactor: number) => {
    // Calculate new zoom level
    const newZoom = viewState.zoom * zoomFactor
    
    console.log(`handleZoom called: centerX=${centerX}, centerY=${centerY}, zoomFactor=${zoomFactor}, newZoom=${newZoom}`);
    
    // Update view state
    viewState.updateView(centerX, centerY, newZoom)
    
    // Explicitly mark this as a zoom operation for proper preview handling
    renderState.lastOperation = 'zoom';
    renderState.isLoading = true; // Start loading immediately to update UI
    
    // Force making the preview canvas visible for zoom operations
    const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
    if (previewCanvas) {
      previewCanvas.style.display = 'block';
      previewCanvas.style.opacity = '1';
      previewCanvas.style.zIndex = '10';
      previewCanvas.style.visibility = 'visible';
    }
    
    // Request a new render whenever zoom changes
    requestRender()
  }, [viewState, renderState, requestRender])
  
  // Track pan events to batch them
  const panDelayTimer = useRef<number | null>(null);
  const pendingPan = useRef<{ x: number, y: number } | null>(null);
  
  // Handle pan events with debouncing
  const handlePan = useCallback((deltaX: number, deltaY: number) => {
    console.log(`handlePan called: deltaX=${deltaX}, deltaY=${deltaY}`);
    
    // Update view state immediately for visual feedback
    viewState.updateView(
      viewState.centerX + deltaX,
      viewState.centerY + deltaY,
      viewState.zoom
    )
    
    // Update or create pending pan
    if (pendingPan.current) {
      pendingPan.current.x += deltaX;
      pendingPan.current.y += deltaY;
    } else {
      pendingPan.current = { x: deltaX, y: deltaY };
    }
    
    // Clear any existing timer
    if (panDelayTimer.current !== null) {
      window.clearTimeout(panDelayTimer.current);
    }
    
    // Set a new timer to actually request the render after user stops panning
    panDelayTimer.current = window.setTimeout(() => {
      // Only request render if there's a pending pan
      if (pendingPan.current) {
        console.log(`Executing batched pan: ${pendingPan.current.x}, ${pendingPan.current.y}`);
        
        // Explicitly mark this as a pan operation for proper preview handling
        renderState.lastOperation = 'pan';
        renderState.isLoading = true; // Start loading immediately to update UI
        
        // Force making the preview canvas visible for pan operations
        const previewCanvas = document.querySelector('.preview-canvas') as HTMLCanvasElement;
        if (previewCanvas) {
          previewCanvas.style.display = 'block';
          previewCanvas.style.opacity = '1';
          previewCanvas.style.zIndex = '10';
          previewCanvas.style.visibility = 'visible';
        }
        
        requestRender();
        pendingPan.current = null;
      }
      panDelayTimer.current = null;
    }, 300); // 300ms delay - adjust as needed
  }, [viewState, renderState, requestRender])
  
  // Handle parameter changes
  const handleParamsChange = useCallback((maxIterations: number, colorScheme: string, tileSize: number, maxConcurrency: number) => {
    viewState.updateParams(maxIterations, colorScheme, tileSize, maxConcurrency)
    // Set the operation type to param-change
    renderState.lastOperation = 'param-change'
  }, [viewState, renderState])
  
  // Handle reset
  const handleReset = useCallback(() => {
    viewState.reset()
    renderState.reset()
    
    // Set operation type to reset to differentiate
    renderState.lastOperation = 'reset';
    
    // Request a render with the reset view but preserved quality settings
    requestRender();
  }, [viewState, renderState, requestRender])
  
  // Track the theme preference
  const [isDarkMode, setIsDarkMode] = useState(() => {
    // Check for saved preference first
    if (typeof window !== 'undefined') {
      const savedTheme = localStorage.getItem('theme');
      if (savedTheme) {
        return savedTheme === 'dark';
      }
      // Otherwise check system preference
      return window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;
    }
    return false;
  });
  
  // Apply the theme class to the document body
  useEffect(() => {
    if (isDarkMode) {
      document.body.classList.add('dark-mode');
    } else {
      document.body.classList.remove('dark-mode');
    }
    // Save preference to localStorage
    if (typeof window !== 'undefined') {
      localStorage.setItem('theme', isDarkMode ? 'dark' : 'light');
    }
  }, [isDarkMode]);
  
  // Toggle the theme
  const toggleTheme = useCallback(() => {
    setIsDarkMode(prevMode => !prevMode);
  }, []);

  return (
    <div className="fractal-explorer">
      <div className="explorer-sidebar">
        <div className="sidebar-header">
          <div className="app-title">OTelBrot Explorer v1.0</div>
          <button 
            onClick={toggleTheme} 
            className="theme-toggle" 
            title={isDarkMode ? "Light mode" : "Dark mode"}
            aria-label={isDarkMode ? "Switch to light mode" : "Switch to dark mode"}
          >
            {isDarkMode ? "☀️" : "🌙"}
          </button>
        </div>
      
        <ControlPanel
          viewState={viewState}
          renderState={renderState}
          onParamsChange={handleParamsChange}
          onRender={requestRender}
          onReset={handleReset}
        />
      </div>
      
      <div className="explorer-main">
        <FractalCanvas
          viewState={viewState}
          renderState={renderState}
          onZoom={handleZoom}
          onPan={handlePan}
        />
      </div>
      
      <footer className="app-footer">
        <div className="otel-branding">
          OTelBrot Explorer <span className="otel-logo">v1.0</span> | Powered by OpenTelemetry
        </div>
      </footer>
    </div>
  )
}