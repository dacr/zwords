/**
 * Presence module for ZWORDS game
 * Shows in the header how many players have the game open, kept up to date by server-sent events
 */

const Presence = {
    source: null,
    playerId: null,

    /**
     * Open the stream for the given player, once : reopening for the same player does nothing
     * @param {string|null} playerId - Player ID, so that several pages of one player count once
     */
    start: function(playerId) {
        if (!window.EventSource) return; // very old browsers : no indicator
        if (this.source && this.playerId === playerId) return;

        this.stop();
        this.playerId = playerId;
        const url = playerId
            ? `${API.BASE_URL}/api/social/online?playerId=${encodeURIComponent(playerId)}`
            : `${API.BASE_URL}/api/social/online`;

        this.source = new EventSource(url);
        this.source.onmessage = (event) => {
            const count = parseInt(event.data, 10);
            if (!isNaN(count)) this.show(count);
        };
        // A game finished somewhere : the statistics screen refreshes the leaderboard of that language
        this.source.addEventListener('leaderboard', (event) => {
            Stats.onGameFinished(event.data);
        });
        // The browser reconnects by itself, meanwhile the count is shown as stale
        this.source.onerror = () => {
            document.getElementById('online-indicator').classList.add('offline');
        };
    },

    stop: function() {
        if (this.source) {
            this.source.close();
            this.source = null;
        }
    },

    show: function(count) {
        const indicator = document.getElementById('online-indicator');
        const label = `${count.toLocaleString()} player${count > 1 ? 's' : ''} online`;
        document.getElementById('online-count').textContent = count.toLocaleString();
        indicator.title = label;
        indicator.setAttribute('aria-label', label);
        indicator.classList.remove('hidden', 'offline');
    }
};
