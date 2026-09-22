<?php
require_once __DIR__ . '/inc/db.php';
require_once __DIR__ . '/inc/layout.php';

render_header('홈');
?>
<h2>메뉴</h2>
<ul id="menu">
  <li><a href="search.php">문항 검색</a> — 키워드 · 단원 · 난이도 · 태그로 문항을 찾습니다.</li>
  <li><a href="register.php">문항 등록</a> — 새 문항을 등록합니다.</li>
  <li><a href="units.php">단원 목록</a> — 단원별 공개 문항 수를 확인합니다.</li>
</ul>
<?php
render_footer();
