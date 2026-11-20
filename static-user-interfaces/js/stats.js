/**
 * Statistics module for ZWORDS game
 * Renders the statistics screen : the player's own results, today's puzzle and all players, for one language
 */

const Stats = {
    elements: {},

    // Language shown on the statistics screen, independent from the game language
    language: null,

    // Used to drop the results of an older request when the language changes quickly
    requestId: 0,
    leaderboardRequestId: 0,

    // Leaderboard period shown : today, week, month or all
    leaderboardPeriod: 'today',

    // Groups the refreshes when several games finish within a short time
    refreshTimer: null,

    /**
     * Initialize the statistics module
     */
    init: function() {
        this.elements = {
            screen: document.getElementById('stats-screen'),
            languageSelect: document.getElementById('stats-language-select'),
            player: document.getElementById('stats-player'),
            today: document.getElementById('stats-today'),
            global: document.getElementById('stats-global'),
            leaderboard: document.getElementById('leaderboard-content'),
            periodButtons: document.querySelectorAll('.leaderboard-period')
        };

        this.elements.languageSelect.addEventListener('change', (e) => {
            this.load(e.target.value);
        });

        this.elements.periodButtons.forEach(button => {
            button.addEventListener('click', () => {
                this.leaderboardPeriod = button.dataset.period;
                this.elements.periodButtons.forEach(b => b.setAttribute('aria-pressed', String(b === button)));
                this.loadLeaderboard(false);
            });
        });
    },

    /**
     * A game just finished (live event) : refresh what the screen shows for its language
     * @param {string} language - Language of the finished game
     */
    onGameFinished: function(language) {
        if (this.elements.screen.classList.contains('hidden') || language !== this.language) return;
        clearTimeout(this.refreshTimer);
        this.refreshTimer = setTimeout(() => this.load(this.language, true), 1500);
    },

    /**
     * Fill the language selector and load the statistics of the current game language
     */
    show: function() {
        const languages = Game.state.availableLanguages;
        const current = languages.includes(this.language) ? this.language : Game.state.currentLanguage;

        this.elements.languageSelect.innerHTML = '';
        languages.forEach(lang => {
            const option = document.createElement('option');
            option.value = lang;
            option.textContent = lang;
            option.selected = lang === current;
            this.elements.languageSelect.appendChild(option);
        });

        this.load(this.elements.languageSelect.value || current);
    },

    /**
     * Load and render every section for the given language
     * @param {string} language - Language key
     */
    load: async function(language, silent = false) {
        this.language = language;
        const requestId = ++this.requestId;
        const player = Game.state.player;

        // a live refresh keeps the current content until the new one is there
        if (!silent) {
            for (const section of [this.elements.player, this.elements.today, this.elements.global]) {
                section.innerHTML = '<p class="stats-empty">Loading&hellip;</p>';
            }
        }
        this.loadLeaderboard(silent);

        const [playerStats, info] = await Promise.all([
            player ? API.getPlayerStatistics(language, player.playerId).catch(() => null) : Promise.resolve(null),
            API.getGameInfo().catch(() => null)
        ]);

        if (requestId !== this.requestId) return; // a newer language was selected meanwhile

        const todayTries = this.todayWinTries(language);
        this.elements.player.innerHTML = this.renderPlayer(player, playerStats, todayTries);
        this.elements.today.innerHTML = info
            ? this.renderToday(info.playedTodayStats?.[language], todayTries)
            : this.renderUnavailable('Today\'s puzzle');
        this.elements.global.innerHTML = info
            ? this.renderGlobal(info.playedStats?.[language], info.dictionaryStats?.[language])
            : this.renderUnavailable('All players');
    },

    /**
     * Number of tries of today's game when the player won it in this language, to highlight it
     * @param {string} language - Language key
     * @returns {number|null}
     */
    todayWinTries: function(language) {
        const game = Game.state.currentGame;
        if (!game || !game.finished || language !== Game.state.currentLanguage) return null;
        return (game.winRank !== undefined && game.winRank !== null) ? game.rows.length : null;
    },

    /**
     * Load and render the leaderboard of the shown language and period
     * @param {boolean} silent - keep the current content until the new one is there
     */
    loadLeaderboard: async function(silent) {
        const requestId = ++this.leaderboardRequestId;
        const player = Game.state.player;
        if (!silent) this.elements.leaderboard.innerHTML = '<p class="stats-empty">Loading&hellip;</p>';

        let board = null;
        try {
            board = await API.getLeaderboard(this.language, this.leaderboardPeriod, player ? player.playerId : null);
        } catch (error) {
            board = null;
        }
        if (requestId !== this.leaderboardRequestId) return; // another period or language was selected meanwhile

        this.elements.leaderboard.innerHTML = board
            ? this.renderLeaderboard(board, player)
            : '<p class="stats-empty">The leaderboard is unavailable right now, please try again later.</p>';
    },

    renderLeaderboard: function(board, player) {
        const period = board.period;
        const description = {
            today: 'Today\'s winners: fewest tries first, then the first to find the word.',
            week: `Since Monday ${this.day(board.from)}: most won games, then fewest tries on average.`,
            month: `Since ${this.day(board.from)}: most won games, then fewest tries on average.`,
            all: `Every language together, at least 20 games played: best win rate first.`
        }[period] || '';
        const empty = {
            today: 'Nobody has found today\'s word yet.',
            week: 'No game finished this week yet.',
            month: 'No game finished this month yet.',
            all: 'Nobody has played 20 games yet.'
        }[period] || 'Nobody yet.';

        const rows = board.entries.map(entry => this.renderLeaderboardRow(entry, period));
        if (board.you && !board.entries.some(entry => entry.you)) {
            rows.push('<li class="leaderboard-gap" aria-hidden="true">&hellip;</li>');
            rows.push(this.renderLeaderboardRow(board.you, period));
        }

        let hint = '';
        if (!player || !player.pseudo) {
            hint = 'Choose a pseudo in the settings to appear on the leaderboard.';
        } else if (player.showOnLeaderboard === false) {
            hint = 'You are hidden from the leaderboard, you can change it in the settings.';
        }

        return `
            <p class="stats-note leaderboard-description">${description}</p>
            ${rows.length > 0 ? `<ol class="leaderboard">${rows.join('')}</ol>` : `<p class="stats-empty">${empty}</p>`}
            ${hint ? `<p class="stats-note">${hint}</p>` : ''}
        `;
    },

    renderLeaderboardRow: function(entry, period) {
        let score;
        if (period === 'today') {
            const tries = Math.round(entry.averageTries || 0);
            score = `${tries} ${tries > 1 ? 'tries' : 'try'}`;
        } else if (period === 'all') {
            score = `${entry.winRate}% <span class="stats-muted">${this.number(entry.won)} won</span>`;
        } else {
            const average = entry.averageTries != null ? ` <span class="stats-muted">${entry.averageTries.toFixed(1)} avg</span>` : '';
            score = `${this.number(entry.won)} won${average}`;
        }
        return `
            <li class="leaderboard-row${entry.you ? ' you' : ''}">
                <span class="leaderboard-rank">${entry.rank}</span>
                <span class="leaderboard-pseudo">${this.escape(entry.pseudo)}${entry.you ? ' <span class="leaderboard-you">you</span>' : ''}</span>
                <span class="leaderboard-score">${score}</span>
            </li>`;
    },

    day: function(isoDay) {
        if (!isoDay) return '';
        const [year, month, day] = isoDay.split('-').map(Number);
        return new Date(year, month - 1, day).toLocaleDateString('en-GB', { day: 'numeric', month: 'long' }); // inside an English sentence
    },

    /**
     * Section : the player's own results
     */
    renderPlayer: function(player, stats, todayTries) {
        // A player's results are kept for every language together, the language selector doesn't change them
        const title = (player && player.pseudo ? `Your results, ${this.escape(player.pseudo)}` : 'Your results')
            + ' <span class="stats-subtitle">all languages</span>';
        if (!player) {
            return `<h3>Your results</h3><p class="stats-empty">Play a game to see your own statistics here.</p>`;
        }
        if (!stats) return this.renderUnavailable('Your results');
        if (stats.playedCount === 0 && stats.activeCount === 0) {
            return `<h3>${title}</h3><p class="stats-empty">No game played in this language yet.</p>`;
        }

        const letters = stats.goodPlaceLetterCount + stats.wrongPlaceLetterCount + stats.unusedLetterCount;
        return `
            <h3>${title}</h3>
            ${this.renderTiles([
                ['Played', stats.playedCount],
                ['Win rate', this.percent(stats.wonCount, stats.playedCount)],
                ['Won', stats.wonCount],
                ['Lost', stats.lostCount]
            ])}
            ${stats.activeCount > 0 ? `<p class="stats-note">${this.number(stats.activeCount)} game${stats.activeCount > 1 ? 's' : ''} in progress</p>` : ''}
            <h4>Wins by number of tries</h4>
            ${this.renderDistribution(stats.wonIn, stats.lostCount, todayTries)}
            ${letters > 0 ? `
                <h4>Letters you tried</h4>
                ${this.renderLetters(stats.goodPlaceLetterCount, stats.wrongPlaceLetterCount, stats.unusedLetterCount)}
            ` : ''}
        `;
    },

    /**
     * Section : today's puzzle, all players
     */
    renderToday: function(today, todayTries) {
        if (!today || today.playedCount === 0) {
            return `<h3>Today's puzzle</h3><p class="stats-empty">Nobody has finished today's puzzle in this language yet.</p>`;
        }
        return `
            <h3>Today's puzzle <span class="stats-subtitle">${this.escape(today.dailyGameId)}</span></h3>
            ${this.renderTiles([
                ['Finished', today.playedCount],
                ['Win rate', this.percent(today.wonCount, today.playedCount)],
                ['Won', today.wonCount],
                ['Lost', today.lostCount]
            ])}
            <h4>Wins by number of tries</h4>
            ${this.renderDistribution(today.wonIn, today.lostCount, todayTries)}
        `;
    },

    /**
     * Section : all players, since the beginning
     */
    renderGlobal: function(played, dictionary) {
        if (!played || played.playedCount === 0) {
            return `<h3>All players</h3><p class="stats-empty">No game finished in this language yet.</p>`;
        }
        return `
            <h3>All players, all time</h3>
            ${this.renderTiles([
                ['Played', played.playedCount],
                ['Win rate', this.percent(played.wonCount, played.playedCount)],
                ['Won', played.wonCount],
                ['Lost', played.lostCount]
            ])}
            <h4>Wins by number of tries</h4>
            ${this.renderDistribution(played.wonIn, played.lostCount, null)}
            ${dictionary ? `<p class="stats-note">Words are picked among ${this.number(dictionary.filteredSelectedWordsCount)} candidates, ${this.number(dictionary.filteredAcceptableWordsCount)} words are accepted as guesses.</p>` : ''}
        `;
    },

    renderUnavailable: function(title) {
        return `<h3>${title}</h3><p class="stats-empty">Statistics are unavailable right now, please try again later.</p>`;
    },

    /**
     * Big number tiles
     * @param {Array<[string, number|string]>} tiles - label and value pairs
     */
    renderTiles: function(tiles) {
        return `<div class="stats-tiles">${tiles.map(([label, value]) => `
            <div class="stats-tile">
                <span class="stats-value">${typeof value === 'number' ? this.number(value) : value}</span>
                <span class="stats-label">${label}</span>
            </div>`).join('')}
        </div>`;
    },

    /**
     * Horizontal bars : wins in 1 to N tries, then lost games. The bar of today's win is highlighted.
     * @param {Object} wonIn - counts keyed "tried1", "tried2", ...
     * @param {number} lostCount - lost games
     * @param {number|null} highlightTries - today's number of tries when won
     */
    renderDistribution: function(wonIn, lostCount, highlightTries) {
        const counts = wonIn || {};
        const triesFound = Object.keys(counts)
            .map(key => parseInt(key.replace('tried', ''), 10))
            .filter(n => !isNaN(n));
        const maxTries = Math.max(Game.state.maxAttempts || 6, ...triesFound);

        const rows = [];
        for (let tries = 1; tries <= maxTries; tries++) {
            rows.push({ label: String(tries), count: counts[`tried${tries}`] || 0, kind: tries === highlightTries ? 'today' : 'won' });
        }
        rows.push({ label: '&times;', count: lostCount || 0, kind: 'lost', title: 'Lost' });

        const max = Math.max(1, ...rows.map(row => row.count));
        return `<div class="stats-distribution">${rows.map(row => `
            <div class="stats-bar-row"${row.title ? ` title="${row.title}"` : ''}>
                <span class="stats-bar-label">${row.label}</span>
                <span class="stats-bar-track">
                    <span class="stats-bar ${row.kind}" style="width: ${Math.max(row.count / max * 100, row.count > 0 ? 6 : 0)}%"></span>
                    <span class="stats-bar-count">${this.number(row.count)}</span>
                </span>
            </div>`).join('')}
        </div>`;
    },

    /**
     * Stacked bar of the letters tried : right place, wrong place, not in the word
     */
    renderLetters: function(good, wrong, unused) {
        const total = good + wrong + unused;
        const parts = [
            ['correct', 'Right place', good],
            ['present', 'Wrong place', wrong],
            ['absent', 'Not in the word', unused]
        ];
        return `
            <div class="stats-letters" role="img" aria-label="${parts.map(([, label, n]) => `${label} ${this.percent(n, total)}`).join(', ')}">
                ${parts.map(([kind, , n]) => n > 0 ? `<span class="stats-letters-part ${kind}" style="flex-grow: ${n}"></span>` : '').join('')}
            </div>
            <ul class="stats-legend">
                ${parts.map(([kind, label, n]) => `<li><span class="stats-swatch ${kind}"></span>${label} <strong>${this.percent(n, total)}</strong> <span class="stats-muted">(${this.number(n)})</span></li>`).join('')}
            </ul>
        `;
    },

    percent: function(part, total) {
        return total > 0 ? `${Math.round(part * 100 / total)}%` : '–';
    },

    number: function(n) {
        return Number(n || 0).toLocaleString();
    },

    escape: function(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }
};
