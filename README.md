# nrepl-refactor

nREPL middleware for refactoring.  This is an attempt to create a base that can be used by various editors such as vim/emacs/light-table, without resorting to re-implementing each of the refactors each editor's plugin language.

Currently only contains (poorly manually tested) refactoring to thread and unthread code.  No integrations have been done with any editors.  Vim-fireplace has a command to replace a code with its evaluated form, it seems like it might be straight forward to replace forms with the results from the refactor functions instead of eval.

## Usage

You can play with it by just evaling forms in the comment at the button of nrepl_refactor.clj.  To interact with it properly see the nrepl readme, particularly the bits about middleware.

## License

Copyright © 2014 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
