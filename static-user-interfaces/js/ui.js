/**
 * UI Module for ZWORDS Game
 * Handles the user interface elements and interactions
 */

const UI = {
    // DOM elements
    elements: {
        welcomeScreen: null,
        gameScreen: null,
        pseudoInput: null,
        languageSelect: null,
        gameLanguageSelect: null,
        startGameBtn: null,
        playerPseudo: null,
        wordsGrid: null,
        keyboard: null,
        gameOverModal: null,
        gameResult: null,
        secretWordDisplay: null,
        gameSummary: null,
        copyResultBtn: null,
        closeModalBtn: null
    },

    // Current UI state
    state: {
        isAnimating: false
    },

    /**
     * Initialize the UI module
     */
    init: function() {
        // Cache DOM elements
        this.elements = {
            welcomeScreen: document.getElementById('welcome-screen'),
            gameScreen: document.getElementById('game-screen'),
            pseudoInput: document.getElementById('pseudo-input'),
            languageSelect: document.getElementById('language-select'),
            gameLanguageSelect: document.getElementById('game-language-select'),
            startGameBtn: document.getElementById('start-game-btn'),
            playerPseudo: document.getElementById('player-pseudo'),
            wordsGrid: document.getElementById('words-grid'),
            keyboard: document.getElementById('keyboard'),
            gameOverModal: document.getElementById('game-over-modal'),
            gameResult: document.getElementById('game-result'),
            secretWordDisplay: document.getElementById('secret-word-display'),
            gameSummary: document.getElementById('game-summary'),
            copyResultBtn: document.getElementById('copy-result-btn'),
            closeModalBtn: document.getElementById('close-modal-btn')
        };

        // Set up event listeners
        this.setupEventListeners();
    },

    /**
     * Set up event listeners for UI elements
     */
    setupEventListeners: function() {
        // Welcome screen events
        this.elements.startGameBtn.addEventListener('click', () => {
            const pseudo = this.elements.pseudoInput.value.trim();
            const language = this.elements.languageSelect.value;

            if (!pseudo) {
                this.showError('Please enter a pseudo');
                return;
            }

            Game.createPlayer(pseudo, language);
        });

        // Game screen events
        this.elements.gameLanguageSelect.addEventListener('change', (e) => {
            Game.changeLanguage(e.target.value);
        });

        // Game over modal events
        this.elements.copyResultBtn.addEventListener('click', () => {
            const summary = Game.generateGameSummary();
            navigator.clipboard.writeText(summary)
                .then(() => {
                    this.elements.copyResultBtn.textContent = 'Copied!';
                    setTimeout(() => {
                        this.elements.copyResultBtn.textContent = 'Copy to Clipboard';
                    }, 2000);
                })
                .catch(err => {
                    console.error('Failed to copy text: ', err);
                    this.showError('Failed to copy to clipboard');
                });
        });

        this.elements.closeModalBtn.addEventListener('click', () => {
            this.elements.gameOverModal.classList.add('hidden');
        });

        // Keyboard events
        document.addEventListener('keydown', (e) => {
            if (this.elements.gameScreen.classList.contains('hidden')) return;

            if (e.key === 'Enter') {
                Game.handleKeyInput('enter');
            } else if (e.key === 'Backspace') {
                Game.handleKeyInput('backspace');
            } else if (/^[a-zA-Z]$/.test(e.key)) {
                Game.handleKeyInput(e.key);
            }
        });

        // Virtual keyboard events
        this.elements.keyboard.addEventListener('click', (e) => {
            const key = e.target.closest('.key');
            if (!key) return;

            const keyValue = key.dataset.key;
            if (keyValue) {
                Game.handleKeyInput(keyValue);
            }
        });
    },

    /**
     * Show welcome screen
     */
    showWelcomeScreen: function() {
        this.elements.welcomeScreen.classList.remove('hidden');
        this.elements.gameScreen.classList.add('hidden');
        this.elements.gameOverModal.classList.add('hidden');

        this.populateLanguageSelectors();
    },

    /**
     * Show game screen
     */
    showGameScreen: function() {
        this.elements.welcomeScreen.classList.add('hidden');
        this.elements.gameScreen.classList.remove('hidden');
        this.elements.gameOverModal.classList.add('hidden');

        this.populateLanguageSelectors();

        // Update player info
        if (Game.state.player) {
            this.elements.playerPseudo.textContent = Game.state.player.pseudo;
        }
    },

    /**
     * Show game over modal
     */
    showGameOverModal: function() {
        const game = Game.state.currentGame;

        if (!game || !game.finished) return;

        // Get player pseudo
        const playerPseudo = Game.state.player?.pseudo || 'Player';

        // Debug information to help diagnose game state issues
        console.log('Game state:', game.state);
        console.log('Game finished:', game.finished);
        console.log('Game hiddenWord:', game.hiddenWord);
        console.log('Game winRank:', game.winRank);

        // Set result message
        // Check for win condition: either state is 'WON' or winRank is defined
        if (game.state === 'WON' || (game.winRank !== undefined && game.winRank !== null)) {
            const triedCount = game.rows.length;
            this.elements.gameResult.textContent = `You Won in ${triedCount} tries!`;
            this.elements.gameResult.style.color = 'var(--correct-color)';
        } else {
            this.elements.gameResult.textContent = 'Game Over';
            this.elements.gameResult.style.color = 'var(--absent-color)';

            // Show the hidden word if the player lost
            if (game.hiddenWord) {
                this.elements.secretWordDisplay.textContent = `The word was: ${game.hiddenWord.toUpperCase()}`;
            }
        }

        // Generate visual summary
        this.elements.gameSummary.innerHTML = '';

        // Add player pseudo to summary
        const pseudoDiv = document.createElement('div');
        pseudoDiv.textContent = `Player: ${playerPseudo}`;
        pseudoDiv.style.marginBottom = '10px';
        pseudoDiv.style.fontWeight = 'bold';
        this.elements.gameSummary.appendChild(pseudoDiv);

        for (const row of game.rows) {
            const rowDiv = document.createElement('div');
            rowDiv.style.display = 'flex';

            for (let i = 0; i < row.givenWord.length; i++) {
                const square = document.createElement('div');
                square.className = 'summary-square';

                if (row.goodPlacesMask.charAt(i) !== '_') {
                    square.classList.add('correct');
                } else if (row.wrongPlacesMask.charAt(i) !== '_') {
                    square.classList.add('present');
                } else {
                    square.classList.add('absent');
                }

                rowDiv.appendChild(square);
            }

            this.elements.gameSummary.appendChild(rowDiv);
        }

        // Reset copy button text
        this.elements.copyResultBtn.textContent = 'Copy to Clipboard';

        // Show modal
        this.elements.gameOverModal.classList.remove('hidden');
    },

    /**
     * Populate language selectors with available languages
     */
    populateLanguageSelectors: function() {
        const languages = Game.state.availableLanguages;
        const defaultLanguage = Game.state.currentLanguage;

        // Clear existing options
        this.elements.languageSelect.innerHTML = '';
        this.elements.gameLanguageSelect.innerHTML = '';

        // Add options for each language
        languages.forEach(lang => {
            const welcomeOption = document.createElement('option');
            welcomeOption.value = lang;
            welcomeOption.textContent = lang;
            welcomeOption.selected = lang === defaultLanguage;
            this.elements.languageSelect.appendChild(welcomeOption);

            const gameOption = document.createElement('option');
            gameOption.value = lang;
            gameOption.textContent = lang;
            gameOption.selected = lang === defaultLanguage;
            this.elements.gameLanguageSelect.appendChild(gameOption);
        });
    },

    /**
     * Update the game board with current game state
     */
    updateGameBoard: function() {
        const game = Game.state.currentGame;

        if (!game) return;

        const wordLength = game.currentMask.length;
        const maxRows = Game.state.maxAttempts;

        // Clear existing grid
        this.elements.wordsGrid.innerHTML = '';

        // Create grid rows
        for (let i = 0; i < maxRows; i++) {
            const rowDiv = document.createElement('div');
            rowDiv.className = 'grid-row';
            rowDiv.dataset.row = i;

            // Create cells for each letter
            for (let j = 0; j < wordLength; j++) {
                const cellDiv = document.createElement('div');
                cellDiv.className = 'grid-cell';
                cellDiv.dataset.col = j;

                // If this is a completed row, fill with played word
                if (i < game.rows?.length) {
                    // Reverse the order of rows - newest at the top
                    const rowIndex = game.rows.length - 1 - i;
                    const row = game.rows[rowIndex];
                    const letter = row.givenWord.charAt(j);
                    cellDiv.textContent = letter.toUpperCase();

                    // Add state class with delay for animation
                    const state = Game.getLetterState(rowIndex, j);
                    if (state) {
                        cellDiv.classList.add('filled');

                        // Add reveal animation with staggered delay
                        setTimeout(() => {
                            cellDiv.classList.add('reveal');
                            setTimeout(() => {
                                cellDiv.classList.add(state);
                            }, 180); // Half of animation duration
                        }, j * 100); // Stagger by column
                    }
                }
                // If this is the current row, fill with current word being typed
                else if (i === game.rows?.length) {
                    // For the first cell, always show the first letter from the mask
                    if (j === 0) {
                        cellDiv.textContent = game.currentMask.charAt(0).toUpperCase();
                        cellDiv.classList.add('filled');
                    }
                    // For other cells, show the current word being typed
                    else if (j < Game.state.currentWord.length) {
                        cellDiv.textContent = Game.state.currentWord.charAt(j).toUpperCase();
                        cellDiv.classList.add('filled');
                    }
                }

                rowDiv.appendChild(cellDiv);
            }

            this.elements.wordsGrid.appendChild(rowDiv);
        }
    },

    /**
     * Update only the current row with the word being typed
     */
    updateCurrentRow: function() {
        const currentRowIndex = Game.getCurrentRowIndex();
        const currentRow = this.elements.wordsGrid.querySelector(`.grid-row[data-row="${currentRowIndex}"]`);

        if (!currentRow) return;

        const cells = currentRow.querySelectorAll('.grid-cell');
        const currentWord = Game.state.currentWord;

        // Update each cell in the current row
        cells.forEach((cell, index) => {
            if (index === 0) {
                // First letter is always fixed
                cell.textContent = Game.state.currentGame.currentMask.charAt(0).toUpperCase();
                cell.classList.add('filled');
            } else if (index < currentWord.length) {
                cell.textContent = currentWord.charAt(index).toUpperCase();
                cell.classList.add('filled');
            } else {
                cell.textContent = '';
                cell.classList.remove('filled');
            }
        });
    },

    /**
     * Create and update the keyboard
     */
    updateKeyboard: function() {
        // Define keyboard layout
        const keyboardLayout = [
            ['q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
            ['a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'],
            ['enter', 'z', 'x', 'c', 'v', 'b', 'n', 'm', 'backspace']
        ];

        // Clear existing keyboard
        this.elements.keyboard.innerHTML = '';

        // Create keyboard rows
        keyboardLayout.forEach(row => {
            const rowDiv = document.createElement('div');
            rowDiv.className = 'keyboard-row';

            // Create keys
            row.forEach(key => {
                const keyDiv = document.createElement('div');
                keyDiv.className = 'key';
                keyDiv.dataset.key = key;

                if (key === 'enter' || key === 'backspace') {
                    keyDiv.classList.add('wide');

                    // Use SVG for special keys
                    if (key === 'enter') {
                        keyDiv.innerHTML = `
                            <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24">
                                <path d="M19 7v4H5.83l3.58-3.59L8 6l-6 6 6 6 1.41-1.41L5.83 13H21V7z"/>
                            </svg>
                        `;
                    } else if (key === 'backspace') {
                        keyDiv.innerHTML = `
                            <svg xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 0 24 24" width="24">
                                <path d="M22 3H7c-.69 0-1.23.35-1.59.88L0 12l5.41 8.11c.36.53.9.89 1.59.89h15c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H7.07L2.4 12l4.66-7H22v14zm-11.59-2L14 13.41 17.59 17 19 15.59 15.41 12 19 8.41 17.59 7 14 10.59 10.41 7 9 8.41 12.59 12 9 15.59z"/>
                            </svg>
                        `;
                    }
                } else {
                    keyDiv.textContent = key.toUpperCase();
                }

                // Add state class based on game state
                const state = Game.getKeyState(key);
                if (state) {
                    keyDiv.classList.add(state);
                }

                rowDiv.appendChild(keyDiv);
            });

            this.elements.keyboard.appendChild(rowDiv);
        });
    },

    /**
     * Show error message
     * @param {string} message - Error message to display
     */
    showError: function(message) {
        // Create error element if it doesn't exist
        let errorElement = document.getElementById('error-message');

        if (!errorElement) {
            errorElement = document.createElement('div');
            errorElement.id = 'error-message';
            errorElement.style.position = 'fixed';
            errorElement.style.top = '20px';
            errorElement.style.left = '50%';
            errorElement.style.transform = 'translateX(-50%)';
            errorElement.style.backgroundColor = '#ff6b6b';
            errorElement.style.color = 'white';
            errorElement.style.padding = '10px 20px';
            errorElement.style.borderRadius = '4px';
            errorElement.style.zIndex = '2000';
            errorElement.style.boxShadow = '0 2px 10px rgba(0, 0, 0, 0.2)';
            document.body.appendChild(errorElement);
        }

        // Set message and show
        errorElement.textContent = message;
        errorElement.style.display = 'block';

        // Hide after 3 seconds
        setTimeout(() => {
            errorElement.style.display = 'none';
        }, 3000);
    }
};
