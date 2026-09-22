<?php
require_once __DIR__ . '/inc/db.php';
require_once __DIR__ . '/inc/layout.php';
require_once __DIR__ . '/vendor/simplelog/Log.php';

use SimpleLog\Log;

$errors  = array();
$notice  = '';
$title   = '';
$stem    = '';
$unitId  = '';
$level   = '';
$tagIds  = array();

try {
    $conn = db_connect();
} catch (RuntimeException $e) {
    render_header('문항 등록', 'register');
    echo '<p class="error">' . h($e->getMessage()) . "</p>\n";
    render_footer();
    exit;
}

// 선택 목록
$units = array();
$res = $conn->query("SELECT id, code, name FROM unit ORDER BY grade ASC, code ASC");
while ($r = $res->fetch_assoc()) {
    $units[] = $r;
}
$res->free();

$tags = array();
$res = $conn->query("SELECT id, name FROM tag ORDER BY id ASC");
while ($r = $res->fetch_assoc()) {
    $tags[] = $r;
}
$res->free();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $title  = isset($_POST['title'])  ? trim((string)$_POST['title'])  : '';
    $stem   = isset($_POST['stem'])   ? trim((string)$_POST['stem'])   : '';
    $unitId = isset($_POST['unit_id']) ? trim((string)$_POST['unit_id']) : '';
    $level  = isset($_POST['level'])  ? trim((string)$_POST['level'])  : '';
    $tagIds = isset($_POST['tags']) && is_array($_POST['tags']) ? $_POST['tags'] : array();

    // ---- 검증 규칙 ----
    // 제목은 5자 이상 (공백 제외 아님, 앞뒤 공백만 제거)
    if (mb_strlen($title, 'UTF-8') < 5) {
        $errors[] = '제목은 5자 이상 입력해야 합니다.';
    }
    if (mb_strlen($title, 'UTF-8') > 200) {
        $errors[] = '제목은 200자를 넘을 수 없습니다.';
    }
    // 지문 필수
    if ($stem === '') {
        $errors[] = '지문을 입력해야 합니다.';
    }
    // 단원 필수
    $unitOk = false;
    foreach ($units as $u) {
        if ((string)$u['id'] === $unitId) {
            $unitOk = true;
        }
    }
    if (!$unitOk) {
        $errors[] = '단원을 선택해야 합니다.';
    }
    // 난이도 1~5
    if (!preg_match('/^[1-5]$/', $level)) {
        $errors[] = '난이도는 1~5 사이여야 합니다.';
    }
    // 태그는 목록에 있는 것만
    $validTagIds = array();
    foreach ($tagIds as $tid) {
        foreach ($tags as $t) {
            if ((string)$t['id'] === (string)$tid) {
                $validTagIds[] = (int)$tid;
            }
        }
    }

    if (empty($errors)) {
        // 등록 직후에는 검수중(R) 상태로 들어간다 → 검수 완료 전까지 검색 화면에 나오지 않는다.
        $conn->begin_transaction();
        try {
            $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
            $row = $res->fetch_assoc();
            $res->free();
            $newId = (int)$row['next_id'];

            $stmt = $conn->prepare(
                "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
            );
            $unitIdInt = (int)$unitId;
            $levelInt  = (int)$level;
            $stmt->bind_param('iissi', $newId, $unitIdInt, $title, $stem, $levelInt);
            if (!$stmt->execute()) {
                throw new RuntimeException('문항 저장 실패');
            }
            $stmt->close();

            if (!empty($validTagIds)) {
                $stmt = $conn->prepare("INSERT INTO item_tag (item_id, tag_id) VALUES (?, ?)");
                foreach ($validTagIds as $tid) {
                    $stmt->bind_param('ii', $newId, $tid);
                    if (!$stmt->execute()) {
                        throw new RuntimeException('태그 저장 실패');
                    }
                }
                $stmt->close();
            }
            $conn->commit();
            Log::info('item registered', array('id' => $newId, 'level' => $levelInt));
            $notice = '문항 #' . $newId . ' 을(를) 등록했습니다. 검수 완료 후 검색에 노출됩니다.';
            $title = $stem = $unitId = $level = '';
            $tagIds = array();
        } catch (Exception $e) {
            $conn->rollback();
            Log::error('item register failed', array('msg' => $e->getMessage()));
            $errors[] = '저장 중 오류가 발생했습니다.';
        }
    }
}

render_header('문항 등록', 'register');
echo '<h2>문항 등록</h2>' . "\n";

if ($notice !== '') {
    echo '<p id="notice">' . h($notice) . "</p>\n";
}
if (!empty($errors)) {
    echo '<ul id="errors" class="error">' . "\n";
    foreach ($errors as $e) {
        echo '<li>' . h($e) . "</li>\n";
    }
    echo "</ul>\n";
}
?>
<form method="post" action="register.php" id="register">
  <p>
    <label for="title">제목 (5자 이상)</label><br>
    <input type="text" id="title" name="title" size="60" value="<?php echo h($title); ?>">
  </p>
  <p>
    <label for="stem">지문</label><br>
    <textarea id="stem" name="stem" rows="4" cols="60"><?php echo h($stem); ?></textarea>
  </p>
  <p>
    <label for="unit_id">단원 (필수)</label>
    <select id="unit_id" name="unit_id">
      <option value="">-- 선택 --</option>
<?php foreach ($units as $u): ?>
      <option value="<?php echo h($u['id']); ?>"<?php echo ((string)$u['id'] === $unitId) ? ' selected' : ''; ?>><?php echo h($u['code'] . ' ' . $u['name']); ?></option>
<?php endforeach; ?>
    </select>
  </p>
  <p>
    <label for="level">난이도 (1~5)</label>
    <select id="level" name="level">
      <option value="">-- 선택 --</option>
<?php for ($i = 1; $i <= 5; $i++): ?>
      <option value="<?php echo $i; ?>"<?php echo ((string)$i === $level) ? ' selected' : ''; ?>><?php echo $i; ?></option>
<?php endfor; ?>
    </select>
  </p>
  <p>
    태그:
<?php foreach ($tags as $t): ?>
    <label><input type="checkbox" name="tags[]" value="<?php echo h($t['id']); ?>"<?php echo in_array((string)$t['id'], array_map('strval', $tagIds), true) ? ' checked' : ''; ?>> <?php echo h($t['name']); ?></label>
<?php endforeach; ?>
  </p>
  <p><button type="submit">등록</button></p>
</form>
<?php
render_footer();
