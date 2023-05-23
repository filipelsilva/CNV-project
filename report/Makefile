LATEX=lualatex
LATEXFLAGS=

FILE_NAME=report

$(FILE_NAME).pdf: $(FILE_NAME).tex
	$(LATEX) $(LATEXFLAGS) $^

.PHONY: clean

clean:
	find . -type f -name '$(FILE_NAME)*' -not -name '*.tex' -delete
