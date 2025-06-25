/**
 * Main application entry point for ZWORDS game
 */

// Wait for DOM to be fully loaded
document.addEventListener('DOMContentLoaded', () => {
    // Initialize UI
    UI.init();
    
    // Initialize and start the game
    Game.init().catch(error => {
        console.error('Failed to initialize game:', error);
        UI.showError('Failed to initialize game. Please try again.');
    });
});

// Handle service worker for PWA if needed
if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/service-worker.js').then(registration => {
            console.log('ServiceWorker registration successful with scope: ', registration.scope);
        }).catch(error => {
            console.log('ServiceWorker registration failed: ', error);
        });
    });
}