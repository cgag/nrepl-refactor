# nrepl-refactor

nREPL middleware for refactoring.  This is an attempt to create a base that can be used by various editors such as vim/emacs/light-table, without resorting to re-implementing each of the refactors each editor's plugin language.

Currently only contains (poorly manually tested) refactoring to thread and unthread code.  No integrations have been done with any editors.  Vim-fireplace has a command to replace a code with its evaluated form, it seems like it might be straight forward to replace forms with the results from the refactor functions instead of eval.

## Usage

You can play with it by just evaling forms in the comment at the bottom of nrepl_refactor.clj.  See also the nREPL readme middleware section.

## TODO

Some hand-written test cases as well as generative testing (simple-check) that check
  * (= form (unthread-last  (thread-last form)))  
  * (= form (unthread-first (thread-first form)))

Editor integration for vim and light table.

Investigate the refactoring capabilities of clj-refactor.el and clojure-refactoring.

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
