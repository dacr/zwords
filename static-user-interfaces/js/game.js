/**
 * Game module for ZWORDS
 * Handles core game logic and state management
 */

const Game = {
    state: {
        player: null,
        currentLanguage: 'FR', // Default language as per requirements
        availableLanguages: [],
        currentGame: null,
        currentWord: '',
        isGameOver: false,
        errorMessage: null,
        maxAttempts: 6,
        wordLength: 0
    },

    /**
     * Initialize the game
     * @returns {Promise<void>}
     */
    init: async function() {
        try {
            // Get available languages
            const languages = await API.getLanguages();
            this.state.availableLanguages = languages.keys || [];

            // Check if player exists in local storage (for permanent persistence)
            const storedPlayerId = localStorage.getItem('playerId');
            const storedLanguage = localStorage.getItem('language');

            if (storedLanguage && this.state.availableLanguages.includes(storedLanguage)) {
                this.state.currentLanguage = storedLanguage;
            }

            if (storedPlayerId) {
                try {
                    this.state.player = await API.getPlayer(storedPlayerId);
                    await this.startGame();
                    return;
                } catch (error) {
                    console.error('Error retrieving stored player:', error);
                    // Continue to create a new player
                }
            }

            // If no player in storage or retrieval failed, show welcome screen
            UI.showWelcomeScreen();
        } catch (error) {
            console.error('Error initializing game:', error);
            UI.showError('Failed to initialize game. Please try again.');
        }
    },

    /**
     * Create a new player or update existing player and start the game
     * @param {string} pseudo - Player's pseudo
     * @param {string} language - Selected language
     * @returns {Promise<void>}
     */
    createPlayer: async function(pseudo, language) {
        try {
            // Check if there's an existing player ID in localStorage
            const storedPlayerId = localStorage.getItem('playerId');
            let player;

            if (storedPlayerId) {
                // Get the existing player
                player = await API.getPlayer(storedPlayerId);
                // Update the pseudo
                player.pseudo = pseudo;
            } else {
                // Create a new player
                player = await API.getPlayer();
                // Set the pseudo
                player.pseudo = pseudo;
            }

            // Update the player on the server
            player = await API.updatePlayer(player);

            // Store player ID and language in local storage for permanent persistence
            localStorage.setItem('playerId', player.playerId);
            localStorage.setItem('language', language);

            this.state.player = player;
            this.state.currentLanguage = language;

            await this.startGame();
        } catch (error) {
            console.error('Error creating/updating player:', error);
            UI.showError('Failed to create/update player. Please try again.');
        }
    },

    /**
     * Start a new game or continue existing game
     * @returns {Promise<void>}
     */
    startGame: async function() {
        try {
            if (!this.state.player) {
                throw new Error('No player available');
            }

            // Get current game state
            const gameState = await API.getGameState(
                this.state.currentLanguage, 
                this.state.player.playerId
            );

            this.state.currentGame = gameState;
            this.state.isGameOver = gameState.finished;
            this.state.wordLength = gameState.currentMask.length;
            this.state.currentWord = '';

            // Show game screen and update UI
            UI.showGameScreen();
            UI.updateGameBoard();
            UI.updateKeyboard();

            if (this.state.isGameOver) {
                UI.showGameOverModal();
            }
        } catch (error) {
            console.error('Error starting game:', error);
            UI.showError('Failed to start game. Please try again.');
        }
    },

    /**
     * Change the current language and restart the game
     * @param {string} language - New language
     * @returns {Promise<void>}
     */
    changeLanguage: async function(language) {
        if (this.state.currentLanguage === language) return;

        try {
            this.state.currentLanguage = language;
            localStorage.setItem('language', language);

            await this.startGame();
        } catch (error) {
            console.error('Error changing language:', error);
            UI.showError('Failed to change language. Please try again.');
        }
    },

    /**
     * Handle keyboard input
     * @param {string} key - Pressed key
     */
    handleKeyInput: function(key) {
        if (this.state.isGameOver) return;

        key = key.toLowerCase();

        if (key === 'enter') {
            this.submitWord();
        } else if (key === 'backspace') {
            this.removeLetter();
        } else if (/^[a-z]$/.test(key)) {
            this.addLetter(key);
        }
    },

    /**
     * Add a letter to the current word
     * @param {string} letter - Letter to add
     */
    addLetter: function(letter) {
        console.log('addLetter called with:', letter, 'currentWord:', this.state.currentWord);

        if (this.state.currentWord.length < this.state.wordLength) {
            // If this is the first letter, check if it matches the first letter of the mask
            if (this.state.currentWord.length === 0) {
                const firstLetter = this.state.currentGame.currentMask.charAt(0).toLowerCase();
                console.log('First letter of mask:', firstLetter, 'Typed letter:', letter.toLowerCase());

                // If the typed letter matches the first letter of the mask, use it
                // Otherwise, use the first letter from the mask
                if (letter.toLowerCase() === firstLetter) {
                    this.state.currentWord += letter.toLowerCase();
                    console.log('Using typed letter:', this.state.currentWord);
                } else {
                    // If the first letter doesn't match, initialize with the correct first letter
                    this.state.currentWord = firstLetter;
                    console.log('Using mask letter:', this.state.currentWord);
                }

                // For the first letter, update UI immediately to provide visual feedback
                UI.updateCurrentRow();
                console.log('UI updated with first letter:', this.state.currentWord);

                // Add the letter again for the first word to ensure consistency with subsequent words
                // This ensures the first letter is registered immediately for all words
                if (this.state.currentWord.length < this.state.wordLength) {
                    this.state.currentWord += letter.toLowerCase();
                    console.log('Added first letter again for consistency:', this.state.currentWord);
                    UI.updateCurrentRow();
                }
            } else {
                this.state.currentWord += letter;
                console.log('Adding letter to word:', this.state.currentWord);

                // Force UI update immediately
                UI.updateCurrentRow();
                console.log('UI updated with currentWord:', this.state.currentWord);
            }
        }
    },

    /**
     * Remove the last letter from the current word
     */
    removeLetter: function() {
        if (this.state.currentWord.length > 1) {
            // Don't allow removing the first letter as it's fixed
            this.state.currentWord = this.state.currentWord.slice(0, -1);
            UI.updateCurrentRow();
        }
    },

    /**
     * Submit the current word
     * @returns {Promise<void>}
     */
    submitWord: async function() {
        if (this.state.currentWord.length !== this.state.wordLength) {
            UI.showError('Word is not complete');
            return;
        }

        try {
            const result = await API.playWord(
                this.state.currentLanguage,
                this.state.player.playerId,
                this.state.currentWord
            );

            this.state.currentGame = result;
            this.state.isGameOver = result.finished;
            this.state.currentWord = '';

            UI.updateGameBoard();
            UI.updateKeyboard();

            if (this.state.isGameOver) {
                UI.showGameOverModal();
            }
        } catch (error) {
            console.error('Error submitting word:', error);

            if (error.status === 463) {
                UI.showError('Word not in dictionary');
            } else if (error.status === 462) {
                UI.showError('Word length is incorrect');
            } else {
                UI.showError('Failed to submit word. Please try again.');
            }
        }
    },

    /**
     * Get the current row index
     * @returns {number} Current row index
     */
    getCurrentRowIndex: function() {
        return this.state.currentGame?.rows?.length || 0;
    },

    /**
     * Get the letter state (correct, present, absent) for a given position in a row
     * @param {number} rowIndex - Row index
     * @param {number} colIndex - Column index
     * @returns {string|null} Letter state
     */
    getLetterState: function(rowIndex, colIndex) {
        if (!this.state.currentGame || !this.state.currentGame.rows || rowIndex >= this.state.currentGame.rows.length) {
            return null;
        }

        const row = this.state.currentGame.rows[rowIndex];
        const letter = row.givenWord.charAt(colIndex);

        if (row.goodPlacesMask.charAt(colIndex) !== '_') {
            return 'correct';
        } else if (row.wrongPlacesMask.charAt(colIndex) !== '_') {
            return 'present';
        } else if (row.notUsedPlacesMask.charAt(colIndex) !== '_') {
            return 'absent';
        }

        return null;
    },

    /**
     * Get the best state for a letter across all rows
     * @param {string} letter - Letter to check
     * @returns {string} Letter state (correct, present, absent, or empty string)
     */
    getKeyState: function(letter) {
        if (!this.state.currentGame || !this.state.currentGame.rows) {
            return '';
        }

        let bestState = '';

        for (const row of this.state.currentGame.rows) {
            const word = row.givenWord.toLowerCase();

            for (let i = 0; i < word.length; i++) {
                if (word.charAt(i) !== letter) continue;

                if (row.goodPlacesMask.charAt(i) !== '_') {
                    return 'correct'; // Correct is the best state, return immediately
                } else if (row.wrongPlacesMask.charAt(i) !== '_' && bestState !== 'correct') {
                    bestState = 'present';
                } else if (row.notUsedPlacesMask.charAt(i) !== '_' && bestState === '') {
                    bestState = 'absent';
                }
            }
        }

        return bestState;
    },

    /**
     * Generate a summary of the game for sharing
     * @returns {string} Game summary
     */
    generateGameSummary: function() {
        if (!this.state.currentGame || !this.state.currentGame.rows) {
            return '';
        }

        const playerPseudo = this.state.player?.pseudo || 'Player';
        let summary = `ZWORDS ${this.state.currentLanguage} - ${playerPseudo}\n${this.state.currentGame.rows.length}/${this.state.maxAttempts} tries\n\n`;

        // Create a copy of the rows array and reverse it to show oldest first
        const reversedRows = [...this.state.currentGame.rows].reverse();

        for (const row of reversedRows) {
            for (let i = 0; i < row.givenWord.length; i++) {
                if (row.goodPlacesMask.charAt(i) !== '_') {
                    summary += '🟩';
                } else if (row.wrongPlacesMask.charAt(i) !== '_') {
                    summary += '🟨';
                } else {
                    summary += '⬛';
                }
            }
            summary += '\n';
        }

        summary += '\nhttps://zwords.code-examples.org';

        return summary;
    }
};
