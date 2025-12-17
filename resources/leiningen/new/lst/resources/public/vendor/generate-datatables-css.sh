#!/bin/bash
# Script to download Bootswatch and DataTables CSS for all themes and generate datatables-[theme].min.css
# Run this from the 'resources/public/vendor' directory

set -e

# Versions
BOOTSWATCH_VERSION=5.3.3
DATATABLES_VERSION=1.13.7

# Themes (match app.js map exactly, no duplicates)
THEMES="flatly superhero yeti cerulean cosmo cyborg darkly journal litera lumen lux materia minty morph pulse quartz sandstone simplex sketchy slate solar spacelab united vapor zephyr"

# Download DataTables Bootstrap 5 CSS (base)
wget -nc https://cdn.datatables.net/${DATATABLES_VERSION}/css/dataTables.bootstrap5.min.css

for theme in $THEMES; do
  # Download Bootswatch theme CSS if missing or empty
  if [ ! -s "bootswatch-${theme}.min.css" ]; then
    wget -O "bootswatch-${theme}.min.css" "https://cdn.jsdelivr.net/npm/bootswatch@${BOOTSWATCH_VERSION}/dist/${theme}/bootstrap.min.css" || { echo "Failed to download bootswatch-${theme}.min.css"; continue; }
  fi
  # Generate themed DataTables CSS by concatenating Bootswatch and DataTables CSS
  if [ -s "bootswatch-${theme}.min.css" ] && [ -s "dataTables.bootstrap5.min.css" ]; then
    cat bootswatch-${theme}.min.css dataTables.bootstrap5.min.css > datatables-${theme}.min.css
    echo "Generated datatables-${theme}.min.css"
  else
    echo "Skipping ${theme}: missing CSS files."
  fi

done

echo "All DataTables theme CSS files generated."