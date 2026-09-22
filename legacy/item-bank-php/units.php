<?php
require_once __DIR__ . '/inc/db.php';
require_once __DIR__ . '/inc/layout.php';

render_header('단원 목록', 'units');

try {
    $conn = db_connect();
} catch (RuntimeException $e) {
    echo '<p class="error">' . h($e->getMessage()) . "</p>\n";
    render_footer();
    exit;
}

// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
$sql = "SELECT u.id, u.code, u.name, u.grade,
               (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
          FROM unit u
         ORDER BY u.grade ASC, u.code ASC";
$res = $conn->query($sql);
if ($res === false) {
    echo '<p class="error">조회 오류</p>' . "\n";
    render_footer();
    exit;
}

$rows = array();
while ($r = $res->fetch_assoc()) {
    $rows[] = $r;
}
$res->free();

echo '<h2>단원 목록</h2>' . "\n";
echo '<p id="count">단원 ' . count($rows) . '개</p>' . "\n";
echo '<table id="units">' . "\n";
echo "<thead>\n<tr><th>코드</th><th>단원명</th><th>학년</th><th>공개 문항 수</th></tr>\n</thead>\n";
echo "<tbody>\n";
foreach ($rows as $r) {
    echo '<tr>';
    echo '<td>' . h($r['code']) . '</td>';
    echo '<td>' . h($r['name']) . '</td>';
    echo '<td>' . h($r['grade']) . '</td>';
    echo '<td>' . h($r['item_count']) . '</td>';
    echo "</tr>\n";
}
echo "</tbody>\n</table>\n";

render_footer();
