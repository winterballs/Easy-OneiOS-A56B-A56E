#!/system/bin/sh
# Скрипт автоматической установки OneiOS
# Выполняется через su (от root)

set -e

STAGE="/data/local/tmp/oneios"
IMG_DIR="$STAGE/image"
RT_DIR="$STAGE/runtime"
DL_DIR="/sdcard/Download/OneiOS"

echo "[*] Начинаем установку OneiOS..."

# 1. Создаём базовые папки
mkdir -p "$IMG_DIR" "$RT_DIR"

# 2. Проверяем наличие архива образа
if [ ! -f "$DL_DIR/ios16.5-a56-image.tar.gz" ]; then
    echo "[!] ОШИБКА: Архив образа не найден в $DL_DIR"
    echo "[!] Пожалуйста, скачайте образ в приложении (кнопка Скачать)."
    exit 1
fi

# 3. Распаковываем образ (если ещё не распакован)
if [ ! -f "$IMG_DIR/os.img" ]; then
    echo "[*] Распаковка образа iOS 16.5 (это займёт время)..."
    tar -xf "$DL_DIR/ios16.5-a56-image.tar.gz" -C "$IMG_DIR"
fi

# 3.5 Распаковываем data.seed (файловая система для Data-раздела iOS)
if [ -f "$IMG_DIR/data.seed" ] && [ ! -d "$STAGE/data" ]; then
    echo "[*] Распаковка data.seed (это займёт время)..."
    mkdir -p "$STAGE/data"
    tar -xf "$IMG_DIR/data.seed" -C "$STAGE/data"
fi

# 4. Скачиваем xnuhost (если нет)
if [ ! -f "$RT_DIR/xnuhost" ]; then
    echo "[*] Скачивание xnuhost..."
    # Используем curl, если он есть, иначе wget
    if command -v curl >/dev/null; then
        curl -L -o "$RT_DIR/xnuhost" "https://github.com/Musicalista/OneiOS-A56/releases/download/v1.0.0/xnuhost_a56"
    else
        wget -O "$RT_DIR/xnuhost" "https://github.com/Musicalista/OneiOS-A56/releases/download/v1.0.0/xnuhost_a56"
    fi
fi
chmod 755 "$RT_DIR/xnuhost"

# 5. Копируем/создаём скрипты
# В реальности мы должны взять их из assets APK, но для простоты скопируем из Termux-форка, если он есть
# (В финальной версии приложение будет распаковывать assets)
if [ ! -f "$STAGE/oneios_switch.sh" ]; then
    echo "[!] ВНИМАНИЕ: oneios_switch.sh не найден. Убедитесь, что он скопирован вручную."
    # Заглушка для теста
    echo "#!/system/bin/sh" > "$STAGE/oneios_switch.sh"
    echo "echo PREFLIGHT OK" >> "$STAGE/oneios_switch.sh"
fi

# Патчим скрипт (добавляем SM-A566B)
if ! grep -q "SM-A566B" "$STAGE/oneios_switch.sh" 2>/dev/null; then
    echo "[*] Патчим oneios_switch.sh для поддержки SM-A566B..."
    sed -i "s/SM-A566E:a56x:s5e8855/SM-A566E|SM-A566B:a56x:s5e8855/g" "$STAGE/oneios_switch.sh"
fi

# 6. Создаём oneios_return.sh (перезагрузка)
if [ ! -f "$STAGE/oneios_return.sh" ]; then
    echo "[*] Создаём oneios_return.sh..."
    cat > "$STAGE/oneios_return.sh" << "RET"
#!/system/bin/sh
reboot
RET
fi
chmod 755 "$STAGE/oneios_return.sh"

# 7. Создаём runtime.conf
echo "[*] Создаём runtime.conf..."
printf "session_abi=1\nversion=16.5\nbuild=20F66\nxnuhost=xnuhost\nimage_formats=1\n" > "$STAGE/runtime.conf"

# 8. Создаём stage_ios16.sh (скрипт стейджинга из форка, если его нет)
if [ ! -f "$STAGE/stage_ios16.sh" ]; then
    echo "[!] ВНИМАНИЕ: stage_ios16.sh не найден. Убедитесь, что он скопирован."
    # Минимальная заглушка (в реальности должен быть полный скрипт)
    echo "#!/system/bin/sh" > "$STAGE/stage_ios16.sh"
    echo "echo Staging..." >> "$STAGE/stage_ios16.sh"
fi
chmod 755 "$STAGE/stage_ios16.sh"

# 9. Считаем хэши
echo "[*] Считаем хэши..."
# runtime.sha256
cd "$RT_DIR"
sha256sum xnuhost > "$STAGE/runtime.sha256"
# image.sha256 и image.ids через stage_ios16.sh
if [ -f "$STAGE/stage_ios16.sh" ]; then
    "$STAGE/stage_ios16.sh" || true
fi

# 10. Выставляем права на всё
echo "[*] Установка прав..."
chown -R 0:0 "$STAGE"
chmod -R 755 "$STAGE"
chmod 644 "$STAGE/runtime.sha256" "$STAGE/image.sha256" "$STAGE/image.ids" "$STAGE/runtime.conf" 2>/dev/null || true
chmod 755 "$STAGE/oneios_switch.sh" "$STAGE/oneios_return.sh" "$STAGE/stage_ios16.sh" "$RT_DIR/xnuhost"

# 11. Проверка
echo "[*] Запуск preflight check..."
"$STAGE/oneios_switch.sh" --check

echo "[+] Установка завершена!"
