#!/bin/bash

# Быстрый скрипт для ручного тестирования SOCKS5 Proxy
# Использование: ./quick-test.sh

PROXY="socks5h://127.0.0.1:1080"

echo "╔══════════════════════════════════════════╗"
echo "║  SOCKS5 Proxy - Быстрое тестирование     ║"
echo "╚══════════════════════════════════════════╝"
echo ""

# Функция для теста с отображением результата
test_url() {
    local url=$1
    local name=$2

    echo -n "Тест: $name ... "

    if curl -s -m 5 -x "$PROXY" "$url" > /dev/null 2>&1; then
        echo "✅ OK"
    else
        echo "❌ FAIL"
    fi
}

echo "1️⃣  Тесты WHITELIST (прямое подключение)"
echo "───────────────────────────────────────────"
test_url "http://ya.ru" "Яндекс"
test_url "http://mail.ru" "Mail.ru"
test_url "http://vk.com" "ВКонтакте"
test_url "http://habr.com" "Habr"
echo ""

echo "2️⃣  Тесты BLACKLIST (блокировка)"
echo "───────────────────────────────────────────"
test_url "http://badwebsite.com" "badwebsite.com (должен быть заблокирован)"
test_url "http://doubleclick.net" "doubleclick.net (реклама)"
echo ""

echo "3️⃣  Тесты SEGMENT (сегментация)"
echo "───────────────────────────────────────────"
test_url "http://web.telegram.org" "telegram.org"
echo ""

echo "4️⃣  Тесты HTTPS"
echo "───────────────────────────────────────────"
test_url "https://ya.ru" "HTTPS Яндекс"
test_url "https://mail.ru" "HTTPS Mail.ru"
test_url "https://github.com" "HTTPS GitHub"
echo ""

echo "5️⃣  Тесты популярных сайтов"
echo "───────────────────────────────────────────"
test_url "http://rbc.ru" "РБК"
test_url "http://ria.ru" "РИА Новости"
test_url "http://tass.ru" "ТАСС"
test_url "http://interfax.ru" "Интерфакс"
echo ""

echo "✅ Тестирование завершено!"