import { useState } from 'react'
import { ViewState } from '../state/ViewState'
import { RenderState } from '../state/RenderState'
import './ControlPanel.css'

interface ControlPanelProps {
  viewState: ViewState
  renderState: RenderState
  onParamsChange: (maxIterations: number, colorScheme: string, tileSize: number) => void
  onRender: () => void
  onReset: () => void
}

export function ControlPanel({
  viewState,
  renderState,
  onParamsChange,
  onRender,
  onReset
}: ControlPanelProps) {
  const [maxIterations, setMaxIterations] = useState(viewState.maxIterations)
  const [colorScheme, setColorScheme] = useState(viewState.colorScheme)
  const [tileSize, setTileSize] = useState(viewState.tileSize)
  
  // Format progress percentage
  const progressPercent = renderState.progress.toFixed(1)
  
  // Format render time
  const renderTime = renderState.elapsedTimeMs > 0
    ? (renderState.elapsedTimeMs / 1000).toFixed(2)
    : '0.00'
    
  // Estimate remaining time based on progress and elapsed time
  const estimateRemainingTime = (state: RenderState): string => {
    if (state.progress <= 0 || state.elapsedTimeMs <= 0) return "calculating";
    
    // Time per percentage point
    const timePerPercent = state.elapsedTimeMs / state.progress;
    
    // Estimate remaining time
    const remainingPercent = 100 - state.progress;
    const remainingTime = (remainingPercent * timePerPercent) / 1000;
    
    return remainingTime.toFixed(1);
  }
  
  
  // Apply changes when controls are modified
  const applyChanges = () => {
    onParamsChange(maxIterations, colorScheme, tileSize)
  }
  
  return (
    <div className="control-panel">
      <div className="coords">
        <div className="coords-group">
          <span className="label">Center X:</span>
          <span className="value">{viewState.centerX.toFixed(6)}</span>
        </div>
        <div className="coords-group">
          <span className="label">Center Y:</span>
          <span className="value">{viewState.centerY.toFixed(6)}</span>
        </div>
        <div className="coords-group">
          <span className="label">Zoom Level:</span>
          <span className="value">{viewState.zoom.toFixed(4)}</span>
        </div>
      </div>
      
      <div className="controls">
        <div className="control-group">
          <label htmlFor="maxIterations">Iteration Depth</label>
          <input
            id="maxIterations"
            type="range"
            min="50"
            max="1000"
            step="50"
            value={maxIterations}
            onChange={(e) => setMaxIterations(parseInt(e.target.value))}
            onMouseUp={applyChanges}
          />
          <div className="range-values">
            <span>50</span>
            <span className="value">{maxIterations}</span>
            <span>1000</span>
          </div>
        </div>
        
        <div className="control-group">
          <label htmlFor="colorScheme">Color Palette</label>
          <select
            id="colorScheme"
            value={colorScheme}
            onChange={(e) => {
              setColorScheme(e.target.value)
              onParamsChange(maxIterations, e.target.value, tileSize)
            }}
          >
            <option value="classic">Classic</option>
            <option value="fire">Fire</option>
            <option value="ocean">Ocean</option>
            <option value="grayscale">Grayscale</option>
            <option value="rainbow">Rainbow</option>
          </select>
        </div>

        <div className="control-group">
          <label htmlFor="tileSize">Tile Size</label>
          <input
            id="tileSize"
            type="range"
            min="64"
            max="512"
            step="32"
            value={tileSize}
            onChange={(e) => setTileSize(parseInt(e.target.value))}
            onMouseUp={applyChanges}
          />
          <div className="range-values">
            <span>64</span>
            <span className="value">{tileSize}</span>
            <span>512</span>
          </div>
        </div>
      </div>
      
      <div className="buttons">
        <button onClick={onRender} disabled={renderState.isLoading}>
          Render Fractal
        </button>
        <button onClick={onReset} className="secondary">
          Reset View
        </button>
      </div>
      
      {renderState.isLoading || renderState.completedTiles > 0 ? (
        <div className="progress-container">
          <div className="progress-stats">
            <span>Rendering Progress</span>
            <span>{progressPercent}%</span>
          </div>
          <div className="progress-bar">
            <div
              className="progress-fill"
              style={{ width: `${renderState.progress}%` }}
            />
          </div>
          
          <div className="stats-container">
            <div className="stat-card">
              <div className="stat-title">Tiles</div>
              <div className="stat-value">{renderState.completedTiles}/{renderState.totalTiles}</div>
              <div className="stat-detail">
                {renderState.isLoading && renderState.totalTiles > 0 ? 
                  `${Math.round((renderState.completedTiles / renderState.totalTiles) * 100)}% complete` : 
                  "Waiting for tiles"}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-title">Render Time</div>
              <div className="stat-value">{renderTime}s</div>
              <div className="stat-detail">
                {renderState.isLoading ? 
                  `~${estimateRemainingTime(renderState)}s remaining` : 
                  renderState.isComplete() ? "Render finished" : "Ready to render"}
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-title">Status</div>
              <div className="stat-value">
                {renderState.isLoading ? "Processing" : renderState.isComplete() ? "Complete" : "Ready"}
              </div>
              <div className="stat-detail">
                {renderState.isLoading ? 
                  `Processing tiles ${renderState.completedTiles}-${Math.min(renderState.completedTiles + 5, renderState.totalTiles)}...` : 
                  renderState.isComplete() ? `${renderState.completedTiles} tiles rendered in ${renderTime}s` : 
                  "Waiting for render request"}
              </div>
            </div>
          </div>
        </div>
      ) : null}
      
      {renderState.errorMessage && (
        <div className="error-message">
          <strong>Error:</strong> {renderState.errorMessage}
        </div>
      )}
    </div>
  )
}