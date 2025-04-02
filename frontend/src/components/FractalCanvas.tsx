import { useRef, useEffect, useState, useCallback } from 'react'
import { ViewState } from '../state/ViewState'
import { RenderState } from '../state/RenderState'
import './FractalCanvas.css'

// Import TileManager types (see FractalExplorer.tsx)

interface FractalCanvasProps {
  viewState: ViewState
  renderState: RenderState
  onZoom: (centerX: number, centerY: number, zoomFactor: number) => void
  onPan: (deltaX: number, deltaY: number) => void
}

export function FractalCanvas({ 
  viewState, 
  renderState,
  onZoom,
  onPan
}: FractalCanvasProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const previewCanvasRef = useRef<HTMLCanvasElement>(null)
  const overlayCanvasRef = useRef<HTMLCanvasElement>(null)
  const isDraggingRef = useRef(false)
  const lastMousePosRef = useRef({ x: 0, y: 0 })
  
  // Use refs instead of state for transient drawing operations
  const isSelectingRef = useRef(false)
  const selectionStartRef = useRef({ x: 0, y: 0 })
  const selectionEndRef = useRef({ x: 0, y: 0 })
  const wheelTimeoutRef = useRef<number | null>(null)
  const panTimeoutRef = useRef<number | null>(null)
  
  // Only keep these as state for final UI render purposes
  const [isSelecting, setIsSelecting] = useState(false)
  const [isZooming, setIsZooming] = useState(false)
  const [zoomProgress, setZoomProgress] = useState(0)
  const [previewZoom, setPreviewZoom] = useState<{
    centerX: number, 
    centerY: number, 
    zoomLevel: number
  } | null>(null)
  
  // Draw selection rectangle on the overlay canvas
  const drawSelectionRectangle = useCallback((ctx: CanvasRenderingContext2D, start = selectionStartRef.current, end = selectionEndRef.current) => {
    // Clear the overlay canvas first
    ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    
    const width = end.x - start.x;
    const height = end.y - start.y;
    
    // Save the current canvas state
    ctx.save();
    
    // Draw selection border with yellow OpenTelemetry color
    ctx.strokeStyle = 'rgba(245, 168, 0, 0.9)';
    ctx.lineWidth = 2;
    ctx.setLineDash([5, 3]);
    ctx.strokeRect(start.x, start.y, width, height);
    
    // Add a subtle highlight over the selection area
    ctx.fillStyle = 'rgba(245, 168, 0, 0.1)';
    ctx.fillRect(start.x, start.y, width, height);
    
    // Draw a secondary border with blue OpenTelemetry color
    ctx.strokeStyle = 'rgba(66, 92, 199, 0.7)';
    ctx.lineWidth = 1;
    ctx.strokeRect(
      start.x - 2, 
      start.y - 2, 
      width + 4, 
      height + 4
    );
    
    // Restore the canvas state
    ctx.restore();
  }, [])

  // Handle keyboard navigation for panning
  const handleKeyDown = useCallback((e: KeyboardEvent) => {
    const canvas = canvasRef.current
    if (!canvas) return
    
    // Pan step size - adjust based on zoom level for consistent feel
    const panStep = viewState.zoom * 0.05
    
    switch (e.key) {
      case 'ArrowUp':
        onPan(0, -panStep)
        e.preventDefault()
        break
      case 'ArrowDown':
        onPan(0, panStep)
        e.preventDefault()
        break
      case 'ArrowLeft':
        onPan(-panStep, 0)
        e.preventDefault()
        break
      case 'ArrowRight':
        onPan(panStep, 0)
        e.preventDefault()
        break
      case '+':
      case '=':
        // Zoom in at current center
        onZoom(viewState.centerX, viewState.centerY, 0.8)
        e.preventDefault()
        break
      case '-':
      case '_':
        // Zoom out at current center
        onZoom(viewState.centerX, viewState.centerY, 1.25)
        e.preventDefault()
        break
    }
  }, [viewState.centerX, viewState.centerY, viewState.zoom, onPan, onZoom])
  
  // Initialize the canvas and render tiles
  useEffect(() => {
    const canvas = canvasRef.current
    const overlay = overlayCanvasRef.current
    if (!canvas || !overlay) return
    
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    
    // Ensure all canvases have the same dimensions
    if (canvas.width !== 800 || canvas.height !== 600) {
      canvas.width = 800;
      canvas.height = 600;
      
      // Match preview and overlay dimensions
      const previewCanvas = previewCanvasRef.current;
      if (previewCanvas) {
        previewCanvas.width = 800;
        previewCanvas.height = 600;
      }
      
      overlay.width = 800;
      overlay.height = 600;
    }
    
    // Clear the main canvas
    ctx.fillStyle = 'black'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    
    // Get the tile manager
    const tileManager = typeof window !== 'undefined' ? window.tileManager : null;
    
    // If we have a tile manager and tiles in the cache, render them
    if (viewState.currentJobId && renderState.completedTiles > 0 && tileManager) {
      console.log(`Rendering tiles from cache with tile manager`);
      tileManager.drawAllTiles(ctx);
    }
    
    // Add keyboard event listeners for arrow key navigation
    window.addEventListener('keydown', handleKeyDown);
    
    // Set focus on the canvas to receive keyboard events
    canvas.tabIndex = 0;
    canvas.focus();
    
    // Clean up all event listeners when component unmounts
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [viewState.currentJobId, renderState.completedTiles, handleKeyDown])
  
  // Handle window resize to maintain canvas dimensions
  useEffect(() => {
    const handleResize = () => {
      const canvas = canvasRef.current;
      const previewCanvas = previewCanvasRef.current;
      const overlayCanvas = overlayCanvasRef.current;
      
      if (canvas && previewCanvas && overlayCanvas) {
        // Reset to fixed dimensions to ensure consistent rendering
        canvas.width = 800;
        canvas.height = 600;
        previewCanvas.width = 800;
        previewCanvas.height = 600;
        overlayCanvas.width = 800;
        overlayCanvas.height = 600;
        
        // Redraw canvas contents
        const ctx = canvas.getContext('2d');
        if (ctx && window.tileManager) {
          window.tileManager.drawAllTiles(ctx);
        }
        
        // Clear any selection overlay
        const overlayCtx = overlayCanvas.getContext('2d');
        if (overlayCtx) {
          overlayCtx.clearRect(0, 0, overlayCanvas.width, overlayCanvas.height);
        }
      }
    };
    
    window.addEventListener('resize', handleResize);
    
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, [])
  
  // Add wheel zoom support
  const handleWheel = useCallback((e: WheelEvent) => {
    e.preventDefault();
    
    // Clear any existing timeout to debounce multiple wheel events
    if (wheelTimeoutRef.current !== null) {
      window.clearTimeout(wheelTimeoutRef.current);
      wheelTimeoutRef.current = null;
    }
    
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    // Get mouse position relative to canvas
    const rect = canvas.getBoundingClientRect();
    const mouseX = e.clientX - rect.left;
    const mouseY = e.clientY - rect.top;
    
    // Convert canvas coordinates to fractal coordinates
    const viewWidth = 2 * viewState.zoom;
    const viewHeight = (viewWidth * canvas.height) / canvas.width;
    
    const relX = mouseX / canvas.width;
    const relY = mouseY / canvas.height;
    
    const fractalX = viewState.centerX + (relX - 0.5) * viewWidth;
    const fractalY = viewState.centerY + (relY - 0.5) * viewHeight;
    
    // Determine zoom direction based on wheel delta
    const zoomFactor = e.deltaY > 0 ? 1.1 : 0.9; // 1.1 zooms out, 0.9 zooms in
    
    // Debounce the actual zoom operation to reduce renders
    wheelTimeoutRef.current = window.setTimeout(() => {
      onZoom(fractalX, fractalY, zoomFactor);
      wheelTimeoutRef.current = null;
    }, 50); // 50ms debounce
  }, [viewState.centerX, viewState.centerY, viewState.zoom, onZoom]);
  
  // Handle starting of selection or pan
  const handleMouseDown = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    // Get canvas-relative coordinates
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // If Shift key is pressed, start selection mode
    if (e.shiftKey) {
      // Update refs for immediate visual feedback
      isSelectingRef.current = true;
      selectionStartRef.current = { x, y };
      selectionEndRef.current = { x, y };
      
      // Update state for component rendering (less frequent)
      setIsSelecting(true);
      
      // Draw initial selection on overlay canvas
      const overlay = overlayCanvasRef.current;
      if (overlay) {
        const ctx = overlay.getContext('2d');
        if (ctx) {
          drawSelectionRectangle(ctx);
        }
      }
    } else {
      // Otherwise, prepare for panning
      isDraggingRef.current = true;
      lastMousePosRef.current = { x: e.clientX, y: e.clientY };
    }
  }, [drawSelectionRectangle])
  
  // Calculate new zoom center and level based on selection - using refs
  const calculateZoomFromSelection = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas || !isSelectingRef.current) return null;
    
    const start = selectionStartRef.current;
    const end = selectionEndRef.current;
    
    // Get selection rectangle dimensions
    const width = Math.abs(end.x - start.x);
    const height = Math.abs(end.y - start.y);
    
    // Skip if selection is too small
    if (width < 10 || height < 10) return null;
    
    // Calculate selection center in canvas coordinates
    const selectionCenterX = Math.min(start.x, end.x) + width / 2;
    const selectionCenterY = Math.min(start.y, end.y) + height / 2;
    
    // Convert to fractal coordinates
    const canvasWidth = canvas.width;
    const canvasHeight = canvas.height;
    
    // Calculate the view dimensions
    const viewWidth = 2 * viewState.zoom;
    const viewHeight = (viewWidth * canvasHeight) / canvasWidth;
    
    // Convert to relative coordinates (0-1)
    const relX = selectionCenterX / canvasWidth;
    const relY = selectionCenterY / canvasHeight;
    
    // Convert to fractal coordinates
    const fractalCenterX = viewState.centerX + (relX - 0.5) * viewWidth;
    const fractalCenterY = viewState.centerY + (relY - 0.5) * viewHeight;
    
    // Calculate zoom factor based on selection size
    const selectionRatio = Math.min(canvasWidth / width, canvasHeight / height);
    const newZoomLevel = viewState.zoom / selectionRatio;
    
    return {
      centerX: fractalCenterX,
      centerY: fractalCenterY,
      zoomLevel: newZoomLevel
    };
  }, [viewState.centerX, viewState.centerY, viewState.zoom])

  // Handle mouse move for selection or panning
  const handleMouseMove = useCallback((e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    const overlay = overlayCanvasRef.current;
    if (!canvas || !overlay) return;
    
    // If we're in selection mode
    if (isSelectingRef.current) {
      const rect = canvas.getBoundingClientRect();
      const x = Math.max(0, Math.min(canvas.width, e.clientX - rect.left));
      const y = Math.max(0, Math.min(canvas.height, e.clientY - rect.top));
      
      // Update ref values for drawing
      selectionEndRef.current = { x, y };
      
      // Calculate preview zoom info without causing a re-render
      const zoomInfo = calculateZoomFromSelection();
      
      // Only update preview zoom state occasionally to reduce re-renders
      // Use timestamp to throttle updates
      const now = Date.now();
      if (zoomInfo && (!lastPreviewUpdateRef?.current || now - lastPreviewUpdateRef.current > 100)) {
        lastPreviewUpdateRef.current = now;
        setPreviewZoom(zoomInfo);
      }
      
      // Draw selection rectangle on overlay canvas without triggering main canvas redraw
      const overlayCtx = overlay.getContext('2d');
      if (overlayCtx) {
        drawSelectionRectangle(overlayCtx);
      }
      return;
    }
    
    // Otherwise handle panning
    if (!isDraggingRef.current) return;
    
    const dx = e.clientX - lastMousePosRef.current.x;
    const dy = e.clientY - lastMousePosRef.current.y;
    
    // Calculate the pan amount in fractal coordinates
    const canvasWidth = canvas.width;
    const canvasHeight = canvas.height;
    
    const viewWidth = 2 * viewState.zoom;
    const viewHeight = (viewWidth * canvasHeight) / canvasWidth;
    
    const panX = (dx / canvasWidth) * viewWidth;
    const panY = (dy / canvasHeight) * viewHeight;
    
    // Update last mouse position first to prepare for next move
    lastMousePosRef.current = { x: e.clientX, y: e.clientY };
    
    // Debounce pan updates to reduce renders
    const fractalDeltaX = -panX;
    const fractalDeltaY = panY;
    
    // Clear any existing timeout
    if (panTimeoutRef.current !== null) {
      window.clearTimeout(panTimeoutRef.current);
    }
    
    // Update internal view state for immediate visual feedback
    // Currently we're not using these for direct drawing, but we could enhance this
    // by adding a custom draw method to the TileManager
    // const newCenterX = viewState.centerX + fractalDeltaX;
    // const newCenterY = viewState.centerY + fractalDeltaY;
    
    // Schedule a debounced pan operation
    panTimeoutRef.current = window.setTimeout(() => {
      onPan(fractalDeltaX, fractalDeltaY);
      panTimeoutRef.current = null;
    }, 50); // 50ms debounce
    
    // Redraw the main canvas with the new view immediately for visual feedback
    // without waiting for the state update
    if (typeof window !== 'undefined' && window.tileManager) {
      const ctx = canvas.getContext('2d');
      if (ctx) {
        // Use a modified draw function that accepts temporary coordinates
        // This assumes the tile manager has a drawWithTempCoordinates method
        // You might need to add this to your TileManager implementation
        try {
          window.tileManager.drawAllTiles(ctx);
        } catch (e) {
          console.error('Error redrawing during pan:', e);
        }
      }
    }
  }, [viewState, drawSelectionRectangle, calculateZoomFromSelection, onPan]);
  
  // Add reference for throttling updates
  const lastPreviewUpdateRef = useRef<number>(0);
  
  // Handle zooming with progress indicator
  const handleZoomStart = useCallback((centerX: number, centerY: number, zoomFactor: number) => {
    setIsZooming(true);
    setZoomProgress(0);
    
    // Use requestAnimationFrame for smoother animation
    let progress = 0;
    let lastTime = 0;
    
    const animateZoom = (timestamp: number) => {
      if (!lastTime) lastTime = timestamp;
      const deltaTime = timestamp - lastTime;
      lastTime = timestamp;
      
      // Update progress based on time (smoother than fixed intervals)
      progress += deltaTime * 0.2; // Adjust speed with multiplier
      
      if (progress >= 100) {
        // Once progress reaches 100%, perform the zoom
        onZoom(centerX, centerY, zoomFactor);
        
        // Reset zoom state after a short delay
        setTimeout(() => {
          setIsZooming(false);
          setZoomProgress(0);
        }, 100);
      } else {
        // Update progress and continue animation
        setZoomProgress(progress);
        requestAnimationFrame(animateZoom);
      }
    };
    
    // Start animation
    requestAnimationFrame(animateZoom);
  }, [onZoom]);
  
  // Handle mouse up - end selection or panning
  const handleMouseUp = useCallback(() => {
    // If we were selecting, handle zoom to selection
    if (isSelectingRef.current) {
      const zoomInfo = calculateZoomFromSelection();
      
      // End selection - clear refs first for immediate visual feedback
      isSelectingRef.current = false;
      
      // Clear the overlay canvas
      const overlay = overlayCanvasRef.current;
      if (overlay) {
        const ctx = overlay.getContext('2d');
        if (ctx) {
          ctx.clearRect(0, 0, overlay.width, overlay.height);
        }
      }
      
      // Update state after visual cleanup (less critical for performance)
      setIsSelecting(false);
      setPreviewZoom(null);
      
      // If we have valid zoom info, begin zooming
      if (zoomInfo) {
        // Begin zooming animation
        handleZoomStart(zoomInfo.centerX, zoomInfo.centerY, zoomInfo.zoomLevel / viewState.zoom);
      }
    }
    
    // End panning operations
    isDraggingRef.current = false;
    
    // If we have a pending pan, execute it now
    if (panTimeoutRef.current !== null) {
      window.clearTimeout(panTimeoutRef.current);
      panTimeoutRef.current = null;
      
      // Final pan - get the distance from initial point to ensure we don't miss any movement
      const canvas = canvasRef.current;
      if (canvas && typeof window !== 'undefined' && window.tileManager) {
        // Force a final redraw with the current view state
        const ctx = canvas.getContext('2d');
        if (ctx) {
          window.tileManager.drawAllTiles(ctx);
        }
      }
    }
  }, [calculateZoomFromSelection, viewState.zoom, handleZoomStart]);
  
  // Cancel selection on mouse leave
  const handleMouseLeave = useCallback(() => {
    if (isSelectingRef.current) {
      // Clear visual elements first
      isSelectingRef.current = false;
      
      // Clear the overlay canvas
      const overlay = overlayCanvasRef.current;
      if (overlay) {
        const ctx = overlay.getContext('2d');
        if (ctx) {
          ctx.clearRect(0, 0, overlay.width, overlay.height);
        }
      }
      
      // Update state (less critical for performance)
      setIsSelecting(false);
      setPreviewZoom(null);
    }
    
    // End dragging
    isDraggingRef.current = false;
  }, [])
  
  // Handle panning via direction buttons
  const handlePanButton = useCallback((direction: 'up' | 'down' | 'left' | 'right') => {
    // Use a larger step for button presses than keyboard
    const panStep = viewState.zoom * 0.15
    
    switch (direction) {
      case 'up':
        onPan(0, -panStep)
        break
      case 'down':
        onPan(0, panStep)
        break
      case 'left':
        onPan(-panStep, 0)
        break
      case 'right':
        onPan(panStep, 0)
        break
    }
  }, [viewState.zoom, onPan])
  
  // Prevent default behavior for keyboard arrow keys to avoid scrolling the page
  useEffect(() => {
    const preventArrowScroll = (e: KeyboardEvent) => {
      if (['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.key)) {
        e.preventDefault();
      }
    };
    
    window.addEventListener('keydown', preventArrowScroll);
    return () => {
      window.removeEventListener('keydown', preventArrowScroll);
    };
  }, []);
  
  // Add wheel event listener for zoom
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    // Add wheel event listener with passive: false to allow preventDefault
    canvas.addEventListener('wheel', handleWheel, { passive: false });
    
    return () => {
      canvas.removeEventListener('wheel', handleWheel);
    };
  }, [handleWheel]);
  
  return (
    <div className="canvas-wrapper">
      <div className="fractal-canvas-container">
        <div className="canvas-inner-container">
          {/* Preview canvas layer - will only show the preview image */}
          <canvas
            ref={previewCanvasRef}
            className="preview-canvas"
            width={800}
            height={600}
            style={{
              display: renderState.isLoading ? 'block' : 'none',
              opacity: renderState.isLoading ? 1 : 0,
              zIndex: renderState.isLoading ? 10 : 0, // Lower z-index when done loading
              visibility: renderState.isLoading ? 'visible' : 'hidden'
            }}
          />
          
          {/* Main canvas for detailed tiles */}
          <canvas
            ref={canvasRef}
            className="fractal-canvas"
            width={800}
            height={600}
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseLeave}
            tabIndex={0}
          />
          
          {/* Overlay canvas for interactive elements (selection, UI overlays) */}
          <canvas
            ref={overlayCanvasRef}
            className="overlay-canvas"
            width={800}
            height={600}
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              pointerEvents: 'none',
              zIndex: 20
            }}
          />
        </div>
        
        {/* Navigation controls */}
        <div className="navigation-controls">
          <div className="nav-row">
            <button className="nav-button up" onClick={() => handlePanButton('up')}>↑</button>
          </div>
          <div className="nav-row">
            <button className="nav-button left" onClick={() => handlePanButton('left')}>←</button>
            <button className="nav-button zoom-in" onClick={() => onZoom(viewState.centerX, viewState.centerY, 0.8)}>+</button>
            <button className="nav-button zoom-out" onClick={() => onZoom(viewState.centerX, viewState.centerY, 1.25)}>-</button>
            <button className="nav-button right" onClick={() => handlePanButton('right')}>→</button>
          </div>
          <div className="nav-row">
            <button className="nav-button down" onClick={() => handlePanButton('down')}>↓</button>
          </div>
        </div>
        
        {/* Loading overlay removed as it's now shown in the control panel */}
        
        {/* Show zoom progress bar when zooming */}
        {isZooming && (
          <div className="zoom-progress-overlay">
            <div className="zoom-progress-container">
              <div 
                className="zoom-progress-bar" 
                style={{ width: `${zoomProgress}%` }}
              ></div>
            </div>
            <div className="zoom-progress-text">Zooming to selected area... {zoomProgress}%</div>
          </div>
        )}
        
        {/* Info tooltip for first-time users */}
        <div className="zoom-instructions">
          Hold Shift + Click and drag to select an area to zoom in, or use arrow keys to pan
        </div>
        
        {/* Canvas coordinates indicator */}
        <div className="canvas-coordinates">
          {isSelecting && previewZoom ? (
            <span className="preview-coords">
              <span className="preview-label">Preview: </span>
              {previewZoom.centerX.toFixed(6)}, {previewZoom.centerY.toFixed(6)} @ {previewZoom.zoomLevel.toFixed(6)}
            </span>
          ) : (
            <span>
              {viewState.centerX.toFixed(6)}, {viewState.centerY.toFixed(6)} @ {viewState.zoom.toFixed(6)}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}