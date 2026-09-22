<?php
/**
 * 공통 레이아웃 (머리말 · 꼬리말)
 * 표 안에는 세션 · 시각 같은 값을 넣지 않는다.
 */

function render_header($title, $active = '')
{
    $menus = array(
        'search'   => array('search.php',   '문항 검색'),
        'register' => array('register.php', '문항 등록'),
        'units'    => array('units.php',    '단원 목록'),
    );
    echo "<!DOCTYPE html>\n";
    echo "<html lang=\"ko\">\n<head>\n";
    echo "<meta charset=\"UTF-8\">\n";
    echo "<title>" . h($title) . " - 문항 은행</title>\n";
    echo "<style>\n";
    echo "body{font-family:sans-serif;margin:20px;color:#222}\n";
    echo "nav a{margin-right:16px}\n";
    echo "nav a.active{font-weight:bold;text-decoration:none}\n";
    echo "table{border-collapse:collapse;margin-top:8px}\n";
    echo "th,td{border:1px solid #999;padding:4px 8px;text-align:left}\n";
    echo "th{background:#eee}\n";
    echo "form.cond label{margin-right:12px}\n";
    echo ".error{color:#b00}\n";
    echo ".summary{color:#555;font-size:90%}\n";
    echo "</style>\n";
    echo "</head>\n<body>\n";
    echo "<header>\n<h1><a href=\"index.php\">문항 은행</a></h1>\n<nav>\n";
    foreach ($menus as $key => $m) {
        $cls = ($key === $active) ? ' class="active"' : '';
        echo '<a href="' . h($m[0]) . '"' . $cls . '>' . h($m[1]) . "</a>\n";
    }
    echo "</nav>\n</header>\n<main>\n";
}

function render_footer()
{
    echo "</main>\n";
    echo "<footer>\n<hr>\n<small>문항 은행 v1.4 (내부용)</small>\n</footer>\n";
    echo "</body>\n</html>\n";
}
