# Project requirements

## User interface requirements

### User interface user requirements

- This is a wordle like game named "ZWORDS"
  - All the game logic is available in the provided REST API
- Rule of the game
  - You have six tries max to guess the daily chosen secret word
  - Show the first letter of the word to guess
    - this first letter cannot be removed, it is a fixed choice
    - the API will provide the first letter to display
- User interface structure is made of
  - a header section with
    - game title `ZWORDS` on the left (colorful, animated, and big)
    - show user pseudo on the center
    - selected dictionary on the right (can be modified)
  - a game board with
    - words grid to show the already played words
      - Use green for well-placed letters, orange for misplaced ones and gray for others
    - displayed keyboard
      - Use SVG symbols for backspace and enter keys
      - Show a well-displayed keyboard
  - a footer section with
    - Copyright 2025 Crosson David and a link to the zwords github project (https://github.com/dacr/zwords)
- Overall behavior
  - the language/dictionary can be changed
    - take into account the new size for the word to guess related to the current language/dictionary
  - The board displays words from the top to the bottom
    - first on top
    - the newest played word **below the previous one**
  - When the daily game is finished
    - Display a Win or Lost message
    - Show the word to be guessed if lost
    - In all cases add a "copy to clipboard" option to share the result of the game
      - use colored rectangle (as characters) to show a summary of the just played game
    - same orange, green, and gray colors at the same place
      - this summary should be copied into the clipboard
      - also put the link to the game into this summary
    - Display also a message to invite the player to come back tomorrow for a new word to guess
  - The game can be played with the keyboard or with the mouse
    - Automatically update the shown keyboard layout depending on the currently selected  dictionary language:
      - for `fr` based use AZERTY
      - for `en` based use QWERTY.
- When first started
  - Ask the user for a pseudo and a language dictionary
    - persist permanently this pseudo and dictionary into the current navigator
    - select the `FR` language dictionary as the default one among possible ones (`/api/game/languages` API call)
- Look & feel
  - New-entered word must fit in a single line
  - Make the user interface fun and colorful
    - Add animations on letters
  - the user interface must be reactive
    - it can be played using a smartphone or a desktop computer
    - adapt the game board to the size and form factor of the currently used navigator

### User interface technical requirements

This project provides a web user interface which has the following characteristics
- The user interfaces files are defined in the project directory `static-user-interfaces`
  - The main entry file is the classic `index.html`
  - The backend module named `webapi` is used to :
    - serve all the user interface files
    - provide all API end points for the game
  - to get the API OPENAPI specifications :
    - start the backend : `make run`
    - get the specs : `curl http://127.0.0.1:8090/docs/docs.yaml`
- For graphics SVG can be used.
- This user interface will be deployed on https://zwords.code-examples.org
