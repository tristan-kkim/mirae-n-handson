FROM php:7.4-apache

RUN docker-php-ext-install mysqli \
    && a2enmod rewrite

# 화면 출력 · 로그 인코딩
RUN { \
      echo 'default_charset = "UTF-8"'; \
      echo 'display_errors = Off'; \
      echo 'log_errors = On'; \
      echo 'error_log = /proc/self/fd/2'; \
    } > /usr/local/etc/php/conf.d/itembank.ini

COPY . /var/www/html/

EXPOSE 80
