# UI prompts

## attempt#1

### step 1
Create in a single file `static-user-interfaces/index.html` all the ready to play zwords game user interface, the user interface must be reactive, and for graphics use SVG. The API documentation is available at `http://127.0.0.1:8090/docs/docs.yaml`. The game is a wordle like game, you have 6 tries max to guess the daily chosen secret word, word size differs for each language, and one new word is chosen automatically by the server every day in the chosen language dictionary. Display on the main page what is the current language. Display each entered word, from top to the bottom.   

### results

looks good but has bugs

## attempt#2

### step 1
Create in a single file `static-user-interfaces/index.html` a ready to play 
zwords game user interface:
- the game is wordle like game
- you have 6 tries max to guess the daily chosen secret word
- the user interface must be reactive
- for graphics SVG can be used.
- the game can be played with the keyboard or with the mouse
- show on the main page what is the current language/dictionary
- if language/dictionary is changed, take into account the new word to guess size
- The API documentation can be downloaded with the command `curl http://127.0.0.1:8090/docs/docs.yaml`
- display guessed words from older top to bottom newer
- use green for good place letter and orange for misplaced

### step 2

the full word must fit in a single line 

### step 3

the just entered new word must be shown below the previous one and not at the top

### results

good and playable

