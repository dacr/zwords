# Project requirements

## User interface requirements

### User interface user requirements

- This is a wordle like game named "ZWORDS"
- You have six tries max to guess the daily chosen secret word
- Show the first letter of the word to guess
- The game can be played with the keyboard or with the mouse
  - Use SVG symbols for backspace and enter keys
  - Show a well-displayed keyboard
- Ask the user for a pseudo and a default dictionary on first access
    -  keep this pseudo into the current navigator session.
- Align the game title `ZWORDS` to the left and make it bigger and fun
- Show on the main page what the current language/dictionary is
- If language/dictionary is changed, take into account the new word to guess size
- Use green for well-placed letters and orange for misplaced ones
- Display guessed words from the older one on top and newer below
  - New-entered word must be shown below the previous one
- New-entered word must fit in a single line
- Make the user interface fun and colorful
  - Add animations on letters
- When the daily game is finished
  - Display a Win or Lost message
  - Show the word to be guessed if lost
  - In all cases add a "copy to clipboard" option to share the result of the game
    - use colored rectangle (as character) to show a summary of the just played game
    - this summary should be copied into the clipboard
    - also put the link to the game into this summary 

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
- the user interface must be reactive
  - it can be played using a smartphone or a desktop computer
  - adapt the game board to the size and form factor of the currently used navigator 
- This user interface will be deployed on https://zwords.code-examples.org
