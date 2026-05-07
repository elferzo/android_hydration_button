#!/bin/bash
# Разворачивает плоские файлы репо в структуру Android-проекта
# Запускать из корня репо

set -e

PKG_DIR="app/src/main/java/com/h2owidget/widget"
RES_DIR="app/src/main/res"

mkdir -p "$PKG_DIR"
mkdir -p "$RES_DIR/layout"
mkdir -p "$RES_DIR/xml"
mkdir -p "app/src/main"

# Kotlin-исходники
cp H2OWidget.kt    "$PKG_DIR/"
cp MainActivity.kt "$PKG_DIR/"
cp BootReceiver.kt "$PKG_DIR/"

# Ресурсы
cp widget_layout.xml "$RES_DIR/layout/"
cp widget_info.xml   "$RES_DIR/xml/"

# Манифест
cp AndroidManifest.xml "app/src/main/"

echo "✅ Структура проекта создана"
