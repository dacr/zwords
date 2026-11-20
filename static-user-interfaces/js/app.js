/**
 * Main application entry point for ZWORDS game
 */

// Wait for DOM to be fully loaded
document.addEventListener('DOMContentLoaded', () => {
    // Read before the game starts : showing the game screen clears the #stats address
    const openStats = location.hash === '#stats';

    // Initialize UI
    UI.init();

    // Initialize and start the game, then open the statistics when asked by the address
    Game.init().then(() => {
        if (openStats) UI.showStatsScreen();
    }).catch(error => {
        console.error('Failed to initialize game:', error);
        UI.showError('Failed to initialize game. Please try again.');
    });

    // Typing #stats in the address of an already open page doesn't reload it
    window.addEventListener('hashchange', () => {
        if (location.hash === '#stats') UI.showStatsScreen();
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