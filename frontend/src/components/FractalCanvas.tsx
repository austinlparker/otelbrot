import { useRef, useEffect, useState } from 'react'
import { ViewState } from '../state/ViewState'
import { RenderState } from '../state/RenderState'
import './FractalCanvas.css'

// Define TileManager interface for TypeScript typing
declare global {
  interface Window {
    tileManager: any
  }
}

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
  const isDraggingRef = useRef(false)
  const lastMousePosRef = useRef({ x: 0, y: 0 })
  
  // Selection rectangle state
  const [isSelecting, setIsSelecting] = useState(false)
  const [selectionStart, setSelectionStart] = useState({ x: 0, y: 0 })
  const [selectionEnd, setSelectionEnd] = useState({ x: 0, y: 0 })
  const [isZooming, setIsZooming] = useState(false)
  const [zoomProgress, setZoomProgress] = useState(0)
  const [previewZoom, setPreviewZoom] = useState<{
    centerX: number, 
    centerY: number, 
    zoomLevel: number
  } | null>(null)
  
  // Draw selection rectangle without clearing the screen
  const drawSelectionRectangle = (ctx: CanvasRenderingContext2D) => {
    const width = selectionEnd.x - selectionStart.x;
    const height = selectionEnd.y - selectionStart.y;
    
    // Save the current canvas state
    ctx.save();
    
    // Draw selection border with yellow OpenTelemetry color
    ctx.strokeStyle = 'rgba(245, 168, 0, 0.9)';
    ctx.lineWidth = 2;
    ctx.setLineDash([5, 3]);
    ctx.strokeRect(selectionStart.x, selectionStart.y, width, height);
    
    // Add a subtle highlight over the selection area
    ctx.fillStyle = 'rgba(245, 168, 0, 0.1)';
    ctx.fillRect(selectionStart.x, selectionStart.y, width, height);
    
    // Draw a secondary border with blue OpenTelemetry color
    ctx.strokeStyle = 'rgba(66, 92, 199, 0.7)';
    ctx.lineWidth = 1;
    ctx.strokeRect(
      selectionStart.x - 2, 
      selectionStart.y - 2, 
      width + 4, 
      height + 4
    );
    
    // Restore the canvas state
    ctx.restore();
  }

  // Handle keyboard navigation for panning
  const handleKeyDown = (e: KeyboardEvent) => {
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
  }
  
  // Initialize the canvas and render tiles
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    
    const ctx = canvas.getContext('2d')
    if (!ctx) return
    
    // Clear the canvas
    ctx.fillStyle = 'black'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    
    // Get the tile manager
    const tileManager = typeof window !== 'undefined' ? window.tileManager : null;
    
    // If we have a tile manager and tiles in the cache, render them properly
    if (viewState.currentJobId && renderState.completedTiles > 0 && tileManager) {
      console.log(`Rendering tiles from cache with tile manager`);
      tileManager.drawAllTiles(ctx);
    }
    
    // Draw selection rectangle if active
    if (isSelecting) {
      drawSelectionRectangle(ctx);
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
  }, [viewState, renderState, isSelecting, selectionStart, selectionEnd, isZooming, onZoom, onPan, drawSelectionRectangle, handleKeyDown])
  
  // Handle starting of selection
  const handleMouseDown = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    // Get canvas-relative coordinates
    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;
    
    // If Shift key is pressed, start selection mode
    if (e.shiftKey) {
      setIsSelecting(true);
      setSelectionStart({ x, y });
      setSelectionEnd({ x, y });
    } else {
      // Otherwise, prepare for panning
      isDraggingRef.current = true;
      lastMousePosRef.current = { x: e.clientX, y: e.clientY };
    }
  }
  
  // Calculate new zoom center and level based on selection
  const calculateZoomFromSelection = () => {
    const canvas = canvasRef.current;
    if (!canvas || !isSelecting) return null;
    
    // Get selection rectangle dimensions
    const width = Math.abs(selectionEnd.x - selectionStart.x);
    const height = Math.abs(selectionEnd.y - selectionStart.y);
    
    // Skip if selection is too small
    if (width < 10 || height < 10) return null;
    
    // Calculate selection center in canvas coordinates
    const selectionCenterX = Math.min(selectionStart.x, selectionEnd.x) + width / 2;
    const selectionCenterY = Math.min(selectionStart.y, selectionEnd.y) + height / 2;
    
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
  }

  // Handle mouse move for selection or panning
  const handleMouseMove = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    
    // If we're in selection mode
    if (isSelecting) {
      const rect = canvas.getBoundingClientRect();
      const x = Math.max(0, Math.min(canvas.width, e.clientX - rect.left));
      const y = Math.max(0, Math.min(canvas.height, e.clientY - rect.top));
      setSelectionEnd({ x, y });
      
      // Only update preview zoom info - don't actually zoom yet
      const zoomInfo = calculateZoomFromSelection();
      if (zoomInfo) {
        setPreviewZoom(zoomInfo);
      }
      
      // Force redraw for selection rectangle
      const ctx = canvas.getContext('2d');
      if (ctx) {
        // Get the tile manager to redraw all tiles
        const tileManager = typeof window !== 'undefined' ? window.tileManager : null;
        if (tileManager) {
          tileManager.drawAllTiles(ctx);
        }
        // Draw selection on top
        drawSelectionRectangle(ctx);
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
    
    // Call the pan callback
    onPan(-panX, panY);
  }
  
  // Handle mouse up - end selection or panning
  const handleMouseUp = () => {
    // If we were selecting, handle zoom to selection
    if (isSelecting) {
      const zoomInfo = calculateZoomFromSelection();
      
      // End selection
      setIsSelecting(false);
      setPreviewZoom(null);
      
      // If we have valid zoom info, begin zooming
      if (zoomInfo) {
        // Begin zooming
        handleZoomStart(zoomInfo.centerX, zoomInfo.centerY, zoomInfo.zoomLevel / viewState.zoom);
      }
    }
    
    // End dragging
    isDraggingRef.current = false;
  }
  
  // Handle zooming with progress indicator
  const handleZoomStart = (centerX: number, centerY: number, zoomFactor: number) => {
    setIsZooming(true);
    setZoomProgress(0);
    
    // Simulate progress
    const intervalId = setInterval(() => {
      setZoomProgress(prev => {
        const newProgress = prev + 10;
        if (newProgress >= 100) {
          clearInterval(intervalId);
          // Once progress reaches 100%, perform the zoom
          onZoom(centerX, centerY, zoomFactor);
          // Reset zoom state
          setTimeout(() => {
            setIsZooming(false);
            setZoomProgress(0);
          }, 100);
          return 100;
        }
        return newProgress;
      });
    }, 50);
  }
  
  // Cancel selection on mouse leave
  const handleMouseLeave = () => {
    if (isSelecting) {
      setIsSelecting(false);
      setPreviewZoom(null);
    }
    isDraggingRef.current = false;
  }
  
  // Handle panning via direction buttons
  const handlePanButton = (direction: 'up' | 'down' | 'left' | 'right') => {
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
  }
  
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
  
  return (
    <div className="canvas-wrapper">
      <div className="fractal-canvas-container">
        {/* Preview canvas layer - will only show the preview image */}
        <canvas
          ref={previewCanvasRef}
          className="preview-canvas"
          width={800}
          height={600}
          style={{
            display: renderState.isComplete() ? 'none' : 'block'
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
          style={{
            position: 'relative',
            zIndex: 2
          }}
        />
        
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