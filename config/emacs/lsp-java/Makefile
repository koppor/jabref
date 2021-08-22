
# Toplevel directory of jabref
TOP             = ../../..

# lsp-java files
SYMLINK_TARGETS = .classpath .project .settings

all:
	@echo "use make setup to create symnlinks to these files"

setup:
	for f in $(SYMLINK_TARGETS) ; do ln -sf config/emacs/lsp-java/eclipse-jdt$$f $(TOP)/$$f ; done
