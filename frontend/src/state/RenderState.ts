/**
 * Represents the current rendering state of the fractal explorer
 */
export class RenderState {
  currentJobId: string | null
  progress: number
  completedTiles: number
  totalTiles: number
  isLoading: boolean
  errorMessage: string | null
  elapsedTimeMs: number
  lastOperation: string | null // 'zoom', 'pan', 'reset', 'param-change', etc.

  constructor() {
    this.currentJobId = null
    this.progress = 0
    this.completedTiles = 0
    this.totalTiles = 0
    this.isLoading = false
    this.errorMessage = null
    this.elapsedTimeMs = 0
    this.lastOperation = null
  }

  /**
   * Reset the render state
   */
  reset(): void {
    this.currentJobId = null
    this.progress = 0
    this.completedTiles = 0
    this.totalTiles = 0
    this.isLoading = false
    this.errorMessage = null
    this.elapsedTimeMs = 0
    this.lastOperation = 'reset'
  }

  /**
   * Start a new render job
   */
  startRender(jobId: string, operation?: string): void {
    this.currentJobId = jobId
    this.progress = 0
    this.completedTiles = 0
    this.totalTiles = 0
    this.isLoading = true
    this.errorMessage = null
    this.elapsedTimeMs = 0
    
    // Only update lastOperation if provided
    if (operation) {
      this.lastOperation = operation;
    }
  }

  /**
   * Update render progress from a WebSocket message
   */
  updateProgress(progress: number, completedTiles: number, totalTiles: number, elapsedTimeMs: number): void {
    this.progress = progress
    this.completedTiles = completedTiles
    this.totalTiles = totalTiles
    this.elapsedTimeMs = elapsedTimeMs
    
    // Ensure loading indicator stays active during rendering
    if (progress < 100 && completedTiles < totalTiles) {
      this.isLoading = true
    }
  }

  /**
   * Set an error message
   */
  setError(message: string): void {
    this.errorMessage = message
    this.isLoading = false
  }

  /**
   * Complete the render
   */
  completeRender(): void {
    this.isLoading = false
  }

  /**
   * Check if the render is complete
   */
  isComplete(): boolean {
    return this.completedTiles === this.totalTiles && this.totalTiles > 0
  }
}