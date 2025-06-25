/**
 * API module for ZWORDS game
 * Handles all communication with the backend API
 */

const API = {
    BASE_URL: '',  // Base URL is empty since we're serving from the same origin

    /**
     * Get the service status
     * @returns {Promise<Object>} Service status
     */
    getServiceStatus: async function() {
        try {
            const response = await fetch(`${this.BASE_URL}/api/system/status`);
            return await response.json();
        } catch (error) {
            console.error('Error getting service status:', error);
            throw error;
        }
    },

    /**
     * Get game information and global statistics
     * @returns {Promise<Object>} Game information
     */
    getGameInfo: async function() {
        try {
            const response = await fetch(`${this.BASE_URL}/api/system/info`);
            return await response.json();
        } catch (error) {
            console.error('Error getting game info:', error);
            throw error;
        }
    },

    /**
     * Get available language dictionaries
     * @returns {Promise<Object>} Available languages
     */
    getLanguages: async function() {
        try {
            const response = await fetch(`${this.BASE_URL}/api/game/languages`);
            return await response.json();
        } catch (error) {
            console.error('Error getting languages:', error);
            throw error;
        }
    },

    /**
     * Create a new player or get existing player information
     * @param {string|null} playerId - Optional player ID
     * @returns {Promise<Object>} Player information
     */
    getPlayer: async function(playerId = null) {
        try {
            const url = playerId 
                ? `${this.BASE_URL}/api/players/player?playerId=${playerId}`
                : `${this.BASE_URL}/api/players/player`;
            
            const response = await fetch(url);
            
            if (!response.ok) {
                if (response.status === 404) {
                    // Player not found, create a new one
                    return await this.getPlayer();
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error getting player:', error);
            throw error;
        }
    },

    /**
     * Update player information
     * @param {Object} playerData - Player data to update
     * @returns {Promise<Object>} Updated player information
     */
    updatePlayer: async function(playerData) {
        try {
            const response = await fetch(`${this.BASE_URL}/api/players/player`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(playerData)
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error updating player:', error);
            throw error;
        }
    },

    /**
     * Get the current game state
     * @param {string} languageKey - Language key
     * @param {string} playerId - Player ID
     * @returns {Promise<Object>} Current game state
     */
    getGameState: async function(languageKey, playerId) {
        try {
            const response = await fetch(`${this.BASE_URL}/api/game/play/${languageKey}/${playerId}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error getting game state:', error);
            throw error;
        }
    },

    /**
     * Play a word in the current game
     * @param {string} languageKey - Language key
     * @param {string} playerId - Player ID
     * @param {string} word - Word to play
     * @returns {Promise<Object>} Updated game state
     */
    playWord: async function(languageKey, playerId, word) {
        try {
            const response = await fetch(`${this.BASE_URL}/api/game/play/${languageKey}/${playerId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ word })
            });
            
            if (!response.ok) {
                const errorData = await response.json();
                throw {
                    status: response.status,
                    data: errorData
                };
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error playing word:', error);
            throw error;
        }
    },

    /**
     * Get player statistics
     * @param {string} languageKey - Language key
     * @param {string} playerId - Player ID
     * @returns {Promise<Object>} Player statistics
     */
    getPlayerStatistics: async function(languageKey, playerId) {
        try {
            const response = await fetch(`${this.BASE_URL}/api/game/statistics/${languageKey}/${playerId}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            return await response.json();
        } catch (error) {
            console.error('Error getting player statistics:', error);
            throw error;
        }
    }
};