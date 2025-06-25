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
            
            // Check if player exists in session storage
            const storedPlayerId = sessionStorage.getItem('playerId');
            const storedLanguage = sessionStorage.getItem('language');
            
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
     * Create a new player and start the game
     * @param {string} pseudo - Player's pseudo
     * @param {string} language - Selected language
     * @returns {Promise<void>}
     */
    createPlayer: async function(pseudo, language) {
        try {
            // Create a new player
            let player = await API.getPlayer();
            
            // Update player with pseudo
            player.pseudo = pseudo;
            player = await API.updatePlayer(player);
            
            // Store player ID and language in session storage
            sessionStorage.setItem('playerId', player.playerId);
            sessionStorage.setItem('language', language);
            
            this.state.player = player;
            this.state.currentLanguage = language;
            
            await this.startGame();
        } catch (error) {
            console.error('Error creating player:', error);
            UI.showError('Failed to create player. Please try again.');
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
            sessionStorage.setItem('language', language);
            
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
        if (this.state.currentWord.length < this.state.wordLength) {
            // If this is the first letter, ensure it matches the first letter of the mask
            if (this.state.currentWord.length === 0) {
                const firstLetter = this.state.currentGame.currentMask.charAt(0);
                this.state.currentWord = firstLetter;
            } else {
                this.state.currentWord += letter;
            }
            
            UI.updateCurrentRow();
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
        
        let summary = `ZWORDS ${this.state.currentLanguage} ${this.state.currentGame.rows.length}/${this.state.maxAttempts}\n\n`;
        
        for (const row of this.state.currentGame.rows) {
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