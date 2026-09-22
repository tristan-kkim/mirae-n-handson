<?php
/**
 * 문항 검색 화면
 *
 * GET 파라미터
 *   q     : 키워드 (제목 · 지문)
 *   unit  : 단원 코드 (예: M5-1)
 *   level : 난이도 (1~5, 빈값 허용)
 *   tag   : 태그 이름
 *   sort  : 정렬 기준 (id | title | unit | level | created) — 비우면 기본 정렬
 *   dir   : asc | desc
 *   page  : 페이지 번호 (1부터)
 *
 * 응답 HTML
 *   <p id="count">검색 결과 N건</p>
 *   <table id="items"> … <tbody><tr><td>id</td><td>title</td><td>unit</td><td>level</td><td>tags</td></tr>
 *   0건이면 <p id="message">검색 결과가 없습니다</p>
 */

require_once __DIR__ . '/inc/db.php';
require_once __DIR__ . '/inc/layout.php';
require_once __DIR__ . '/vendor/simplelog/Log.php';

use SimpleLog\Log;

mb_internal_encoding('UTF-8');
Log::setThreshold(Log::LEVEL_INFO);

/**
 * 검색 조건(GET 파라미터)을 받아 실행할 SQL · 바인딩 · 화면 보조 정보를 만든다.
 *
 * 반환 배열
 *   sql, count_sql, types, values      — 실행용
 *   summary_html, form, sort_links     — 화면용
 *   warnings                           — 입력값 경고
 *   page, page_size, sort, dir, params — 상태
 *
 * @param array  $params  $_GET
 * @param mysqli $conn    선택 목록(단원 · 태그) 조회에 쓴다
 * @return array
 */
function buildSearchQuery(array $params, $conn)
{
    $where    = " WHERE 1=1";
    $types    = '';
    $values   = array();
    $summary  = array();
    $warnings = array();

    // ------------------------------------------------------------------
    // 파라미터 꺼내기 (배열로 들어오면 첫 값만 쓴다)
    // ------------------------------------------------------------------
    $q = '';
    if (isset($params['q'])) {
        $q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
    }
    $unit = '';
    if (isset($params['unit'])) {
        $unit = is_array($params['unit']) ? (string)reset($params['unit']) : (string)$params['unit'];
    }
    $level = '';
    if (isset($params['level'])) {
        $level = is_array($params['level']) ? (string)reset($params['level']) : (string)$params['level'];
    }
    $tag = '';
    if (isset($params['tag'])) {
        $tag = is_array($params['tag']) ? (string)reset($params['tag']) : (string)$params['tag'];
    }
    $sort = '';
    if (isset($params['sort'])) {
        $sort = is_array($params['sort']) ? (string)reset($params['sort']) : (string)$params['sort'];
    }
    $dir = '';
    if (isset($params['dir'])) {
        $dir = is_array($params['dir']) ? (string)reset($params['dir']) : (string)$params['dir'];
    }
    $pageRaw = '1';
    if (isset($params['page'])) {
        $pageRaw = is_array($params['page']) ? (string)reset($params['page']) : (string)$params['page'];
    }

    // ------------------------------------------------------------------
    // 키워드 (q) — 제목과 지문 양쪽을 LIKE 로 찾는다. 앞뒤 공백은 제거.
    // ------------------------------------------------------------------
    $q = trim($q);
    if (mb_strlen($q, 'UTF-8') > 100) {
        $q = mb_substr($q, 0, 100, 'UTF-8');
        $warnings[] = '키워드가 너무 길어 100자까지만 사용했습니다.';
    }
    if ($q !== '') {
        if (mb_strlen($q, 'UTF-8') === 1) {
            $warnings[] = '키워드가 한 글자라 결과가 많을 수 있습니다.';
        }
        if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
            $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
        }
        $like = '%' . $q . '%';
        $where .= " AND (title LIKE ? OR stem LIKE ?)";
        $types .= 'ss';
        $values[] = $like;
        $values[] = $like;
        $summary[] = '키워드 "' . $q . '"';
        Log::debug('search keyword', array('q' => $q));
    }

    // ------------------------------------------------------------------
    // 단원 (unit) — 단원 코드와 정확히 일치
    // ------------------------------------------------------------------
    $unit = trim($unit);
    if ($unit !== '') {
        if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
            // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
            $warnings[] = '단원 코드 형식이 올바르지 않습니다. (예: M5-1)';
        }
        if ($unit !== strtoupper($unit)) {
            $warnings[] = '단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)';
        }
        $where .= " AND unit_code = ?";
        $types .= 's';
        $values[] = $unit;

        // 요약에 단원 이름을 같이 보여 준다 (없는 코드면 경고)
        $unitName = '';
        if ($conn !== null) {
            $stmt = $conn->prepare("SELECT name, grade FROM unit WHERE code = ?");
            if ($stmt !== false) {
                $stmt->bind_param('s', $unit);
                if ($stmt->execute()) {
                    $res = $stmt->get_result();
                    if ($res !== false) {
                        $r = $res->fetch_assoc();
                        if ($r) {
                            $unitName = $r['name'] . ' (' . $r['grade'] . '학년)';
                        }
                        $res->free();
                    }
                }
                $stmt->close();
            }
        }
        if ($unitName === '') {
            $warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
            $summary[] = '단원 ' . $unit;
        } else {
            $summary[] = '단원 ' . $unit . ' ' . $unitName;
        }
    }

    // ------------------------------------------------------------------
    // 선택 목록 — 단원 · 태그 (검색 폼에 그대로 쓴다)
    // ------------------------------------------------------------------
    $unitOptions = array();
    $unitGroups  = array();
    $unitOptions[] = array('value' => '', 'label' => '전체 단원', 'selected' => ($unit === ''));
    if ($conn !== null) {
        $res = $conn->query("SELECT code, name, grade FROM unit ORDER BY grade ASC, code ASC");
        if ($res !== false) {
            while ($r = $res->fetch_assoc()) {
                $opt = array(
                    'value'    => $r['code'],
                    'label'    => $r['code'] . ' ' . $r['name'],
                    'selected' => ($r['code'] === $unit),
                );
                $unitOptions[] = $opt;
                $g = (int)$r['grade'];
                if (!isset($unitGroups[$g])) {
                    $unitGroups[$g] = array();
                }
                $unitGroups[$g][] = $opt;
            }
            $res->free();
        }
    }

    $tagOptions = array();
    $tagOptions[] = array('value' => '', 'label' => '전체 태그', 'selected' => (trim($tag) === ''));
    if ($conn !== null) {
        $res = $conn->query("SELECT name FROM tag ORDER BY id ASC");
        if ($res !== false) {
            while ($r = $res->fetch_assoc()) {
                $tagOptions[] = array(
                    'value'    => $r['name'],
                    'label'    => $r['name'],
                    'selected' => ($r['name'] === trim($tag)),
                );
            }
            $res->free();
        }
    }

    $levelOptions = array();
    $levelOptions[] = array('value' => '', 'label' => '전체', 'selected' => (trim($level) === ''));
    $levelLabels = array(
        1 => '1 (매우 쉬움)',
        2 => '2 (쉬움)',
        3 => '3 (보통)',
        4 => '4 (어려움)',
        5 => '5 (최상)',
    );
    foreach ($levelLabels as $lv => $label) {
        $levelOptions[] = array(
            'value'    => (string)$lv,
            'label'    => $label,
            'selected' => ((string)$lv === trim($level)),
        );
    }

    // ------------------------------------------------------------------
    // 난이도 (level)
    // ------------------------------------------------------------------
    $level = trim($level);

    // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
    if ($level == '') {
        $where .= " AND level < 5";
    }
    else if (preg_match('/^[1-5]$/', $level)) {
        $where .= " AND level = ?";
        $types .= 'i';
        $values[] = (int)$level;
        $summary[] = '난이도 ' . $level;
    }
    else {
        // 1~5 밖의 값은 그대로 정수로 비교한다 (대개 0건)
        $warnings[] = '난이도는 1~5 사이여야 합니다.';
        $where .= " AND level = ?";
        $types .= 'i';
        $values[] = (int)$level;
        $summary[] = '난이도 ' . (int)$level;
    }

    // ------------------------------------------------------------------
    // 태그 (tag) — 태그 이름과 정확히 일치하는 문항만
    // ------------------------------------------------------------------
    $tag = trim($tag);
    if ($tag !== '') {
        if (strpos($tag, '%') !== false || strpos($tag, '_') !== false) {
            $warnings[] = '태그는 부분 일치를 지원하지 않습니다.';
        }
        if (mb_strlen($tag, 'UTF-8') > 50) {
            $tag = mb_substr($tag, 0, 50, 'UTF-8');
            $warnings[] = '태그 이름이 너무 길어 50자까지만 사용했습니다.';
        }
        $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
                . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
        $types .= 's';
        $values[] = $tag;
        $summary[] = '태그 ' . $tag;

        // 등록된 태그인지 확인 (없어도 조회는 그대로 한다 → 0건)
        $tagKnown = false;
        foreach ($tagOptions as $opt) {
            if ($opt['value'] !== '' && $opt['value'] === $tag) {
                $tagKnown = true;
                break;
            }
        }
        if (!$tagKnown) {
            $warnings[] = '등록되지 않은 태그입니다: ' . $tag;
        }
    }

    // ------------------------------------------------------------------
    // 정렬 (sort · dir)
    // ------------------------------------------------------------------
    $sort = strtolower(trim($sort));
    $dir  = strtolower(trim($dir));
    if ($dir !== 'asc' && $dir !== 'desc') {
        if ($dir !== '') {
            $warnings[] = '정렬 방향은 asc 또는 desc 만 가능합니다.';
        }
        $dir = '';
    }

    $orderBy = '';
    switch ($sort) {
        case 'id':
            if ($dir === 'desc') {
                $orderBy = " ORDER BY id DESC";
            } else {
                $orderBy = " ORDER BY id ASC";
            }
            break;

        case 'title':
            if ($dir === 'desc') {
                $orderBy = " ORDER BY title DESC, id ASC";
            } else {
                $orderBy = " ORDER BY title ASC, id ASC";
            }
            break;

        case 'unit':
            if ($dir === 'desc') {
                $orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
            } else {
                $orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
            }
            break;

        case 'level':
            if ($dir === 'asc') {
                $orderBy = " ORDER BY level ASC, id ASC";
            } else {
                $orderBy = " ORDER BY level DESC, id ASC";
            }
            break;

        case 'created':
            if ($dir === 'asc') {
                $orderBy = " ORDER BY created_at ASC, id ASC";
            } else {
                $orderBy = " ORDER BY created_at DESC, id ASC";
            }
            break;

        case '':
            // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
            $orderBy = " ORDER BY level DESC, id ASC";
            break;

        default:
            $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
            $sort = '';
            $orderBy = " ORDER BY level DESC, id ASC";
            break;
    }
    if ($sort !== '' && $dir === '') {
        // 화면 표시용 기본 방향
        $dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
    }

    // ------------------------------------------------------------------
    // 페이지 (page) — 한 페이지 20건
    // ------------------------------------------------------------------
    $page = 1;
    if (preg_match('/^[0-9]+$/', $pageRaw)) {
        $page = (int)$pageRaw;
    } else if ($pageRaw !== '' && $pageRaw !== '1') {
        $warnings[] = '페이지 번호가 올바르지 않아 1페이지를 표시합니다.';
    }
    if ($page < 1) {
        $page = 1;
    }
    if ($page > 999) {
        $page = 999;
        $warnings[] = '페이지 번호는 999 를 넘을 수 없습니다.';
    }
    $offset = ($page - 1) * 20;

    // ------------------------------------------------------------------
    // 조건 요약 HTML
    // ------------------------------------------------------------------
    $summaryHtml = '';
    if (empty($summary)) {
        $summaryHtml = '<p class="summary">조건 없이 검색했습니다.</p>' . "\n";
    } else {
        $summaryHtml = '<ul class="summary">' . "\n";
        foreach ($summary as $s) {
            $summaryHtml .= '  <li>' . h($s) . '</li>' . "\n";
        }
        $summaryHtml .= '</ul>' . "\n";
    }
    if ($sort !== '') {
        $summaryHtml .= '<p class="summary">정렬: ' . h($sort) . ' ' . h($dir) . '</p>' . "\n";
    }

    // ------------------------------------------------------------------
    // 현재 조건을 유지하는 링크용 쿼리 문자열 (page · sort · dir 제외)
    // ------------------------------------------------------------------
    $baseQuery = array();
    if ($q !== '') {
        $baseQuery['q'] = $q;
    }
    if ($unit !== '') {
        $baseQuery['unit'] = $unit;
    }
    if ($level !== '') {
        $baseQuery['level'] = $level;
    }
    if ($tag !== '') {
        $baseQuery['tag'] = $tag;
    }
    $baseQs = '';
    foreach ($baseQuery as $k => $v) {
        $baseQs .= ($baseQs === '' ? '?' : '&') . rawurlencode($k) . '=' . rawurlencode($v);
    }

    // ------------------------------------------------------------------
    // 열 머리글 정렬 링크
    // ------------------------------------------------------------------
    $sortColumns = array(
        'id'    => 'ID',
        'title' => '제목',
        'unit'  => '단원',
        'level' => '난이도',
    );
    $sortLinks = array();
    foreach ($sortColumns as $key => $label) {
        $nextDir = 'asc';
        $mark = '';
        if ($sort === $key) {
            if ($dir === 'asc') {
                $nextDir = 'desc';
                $mark = ' ▲';
            } else {
                $nextDir = 'asc';
                $mark = ' ▼';
            }
        } else if ($key === 'level') {
            // 난이도는 첫 클릭에 내림차순
            $nextDir = 'desc';
        }
        $href = 'search.php' . ($baseQs === '' ? '?' : $baseQs . '&')
              . 'sort=' . rawurlencode($key) . '&dir=' . $nextDir;
        $sortLinks[$key] = '<a href="' . h($href) . '">' . h($label) . h($mark) . '</a>';
    }
    $sortLinks['tags'] = '태그';

    // ------------------------------------------------------------------
    // 페이지 링크 (총 건수는 실행 후에 알 수 있으므로 앞부분만 만든다)
    // ------------------------------------------------------------------
    $pageQs = $baseQs;
    if ($sort !== '') {
        $pageQs .= ($pageQs === '' ? '?' : '&') . 'sort=' . rawurlencode($sort);
        if ($dir !== '') {
            $pageQs .= '&dir=' . rawurlencode($dir);
        }
    }
    $pageQsPrefix = 'search.php' . ($pageQs === '' ? '?' : $pageQs . '&') . 'page=';

    // ------------------------------------------------------------------
    // 검색 폼 데이터
    // ------------------------------------------------------------------
    $form = array(
        'q'      => $q,
        'unit'   => $unit,
        'level'  => $level,
        'tag'    => $tag,
        'sort'   => $sort,
        'dir'    => $dir,
        'units'  => $unitOptions,
        'levels' => $levelOptions,
        'tags'   => $tagOptions,
    );

    // 폼 select HTML 을 미리 만들어 둔다
    $formHtml = array();

    $html = '<select id="unit" name="unit">' . "\n";
    $html .= '  <option value=""' . ($unit === '' ? ' selected' : '') . '>전체 단원</option>' . "\n";
    if (!empty($unitGroups)) {
        // 학년별로 묶어서 보여 준다
        foreach ($unitGroups as $grade => $opts) {
            $html .= '  <optgroup label="' . h($grade . '학년') . '">' . "\n";
            foreach ($opts as $opt) {
                $html .= '    <option value="' . h($opt['value']) . '"'
                      . ($opt['selected'] ? ' selected' : '') . '>'
                      . h($opt['label']) . '</option>' . "\n";
            }
            $html .= '  </optgroup>' . "\n";
        }
    } else {
        foreach ($unitOptions as $opt) {
            if ($opt['value'] === '') {
                continue;
            }
            $html .= '  <option value="' . h($opt['value']) . '"'
                  . ($opt['selected'] ? ' selected' : '') . '>'
                  . h($opt['label']) . '</option>' . "\n";
        }
    }
    $html .= '</select>';
    $formHtml['unit'] = $html;

    $html = '<select id="level" name="level">' . "\n";
    foreach ($levelOptions as $opt) {
        $html .= '  <option value="' . h($opt['value']) . '"'
              . ($opt['selected'] ? ' selected' : '') . '>'
              . h($opt['label']) . '</option>' . "\n";
    }
    $html .= '</select>';
    $formHtml['level'] = $html;

    $html = '<select id="tag" name="tag">' . "\n";
    foreach ($tagOptions as $opt) {
        $html .= '  <option value="' . h($opt['value']) . '"'
              . ($opt['selected'] ? ' selected' : '') . '>'
              . h($opt['label']) . '</option>' . "\n";
    }
    $html .= '</select>';
    $formHtml['tag'] = $html;

    $html = '<input type="text" id="q" name="q" size="30" maxlength="100" value="' . h($q) . '">';
    $formHtml['q'] = $html;

    $hidden = '';
    if ($sort !== '') {
        $hidden .= '<input type="hidden" name="sort" value="' . h($sort) . '">' . "\n";
    }
    if ($dir !== '') {
        $hidden .= '<input type="hidden" name="dir" value="' . h($dir) . '">' . "\n";
    }
    $formHtml['hidden'] = $hidden;

    // ------------------------------------------------------------------
    // 경고 HTML
    // ------------------------------------------------------------------
    $warningHtml = '';
    if (!empty($warnings)) {
        $warningHtml = '<ul id="warnings" class="error">' . "\n";
        foreach ($warnings as $w) {
            $warningHtml .= '  <li>' . h($w) . '</li>' . "\n";
        }
        $warningHtml .= '</ul>' . "\n";
    }

    // ------------------------------------------------------------------
    // SQL 조립 — 공개 문항 뷰(v_item_public)를 기준으로 조회한다
    // ------------------------------------------------------------------
    $select = "SELECT id, title, unit_code, level, tag_names, created_at";
    $from   = " FROM v_item_public";
    $limit  = " LIMIT 20 OFFSET " . (int)$offset;

    $sql      = $select . $from . $where . $orderBy . $limit;
    $countSql = "SELECT COUNT(*) AS cnt" . $from . $where;

    Log::debug('search query built', array(
        'where' => $where,
        'order' => $orderBy,
        'page'  => $page,
        'binds' => count($values),
    ));

    // 바인딩 개수와 타입 문자열 길이가 다르면 여기서 막는다
    if (strlen($types) !== count($values)) {
        Log::error('bind mismatch', array('types' => $types, 'values' => count($values)));
        throw new RuntimeException('검색 조건 조립 오류');
    }

    return array(
        'sql'          => $sql,
        'count_sql'    => $countSql,
        'types'        => $types,
        'values'       => $values,
        'summary_html' => $summaryHtml,
        'warning_html' => $warningHtml,
        'form'         => $form,
        'form_html'    => $formHtml,
        'sort_links'   => $sortLinks,
        'page_prefix'  => $pageQsPrefix,
        'page'         => $page,
        'page_size'    => 20,
        'sort'         => $sort,
        'dir'          => $dir,
        'warnings'     => $warnings,
        'params'       => array(
            'q'     => $q,
            'unit'  => $unit,
            'level' => $level,
            'tag'   => $tag,
        ),
    );
}

/**
 * 조립된 쿼리를 실행해 총 건수와 현재 페이지 행을 돌려준다.
 *
 * @return array array('total' => int, 'rows' => array)
 */
function runSearchQuery(array $built, $conn)
{
    $total = 0;
    $rows  = array();

    // 총 건수
    $stmt = $conn->prepare($built['count_sql']);
    if ($stmt === false) {
        throw new RuntimeException('건수 조회 준비 실패: ' . $conn->error);
    }
    if ($built['types'] !== '') {
        $refs = array();
        foreach ($built['values'] as $i => $v) {
            $refs[$i] = &$built['values'][$i];
        }
        call_user_func_array(array($stmt, 'bind_param'), array_merge(array($built['types']), $refs));
    }
    if (!$stmt->execute()) {
        throw new RuntimeException('건수 조회 실패: ' . $stmt->error);
    }
    $res = $stmt->get_result();
    if ($res !== false) {
        $r = $res->fetch_assoc();
        $total = (int)$r['cnt'];
        $res->free();
    }
    $stmt->close();

    // 현재 페이지 행
    $stmt = $conn->prepare($built['sql']);
    if ($stmt === false) {
        throw new RuntimeException('검색 준비 실패: ' . $conn->error);
    }
    if ($built['types'] !== '') {
        $refs = array();
        foreach ($built['values'] as $i => $v) {
            $refs[$i] = &$built['values'][$i];
        }
        call_user_func_array(array($stmt, 'bind_param'), array_merge(array($built['types']), $refs));
    }
    if (!$stmt->execute()) {
        throw new RuntimeException('검색 실패: ' . $stmt->error);
    }
    $res = $stmt->get_result();
    if ($res !== false) {
        while ($r = $res->fetch_assoc()) {
            $rows[] = $r;
        }
        $res->free();
    }
    $stmt->close();

    return array('total' => $total, 'rows' => $rows);
}

/**
 * 검색 폼 출력
 */
function renderSearchForm(array $built)
{
    $fh = $built['form_html'];
    echo '<form method="get" action="search.php" class="cond" id="cond">' . "\n";
    echo '  <label for="q">키워드</label>' . $fh['q'] . "\n";
    echo '  <label for="unit">단원</label>' . $fh['unit'] . "\n";
    echo '  <label for="level">난이도</label>' . $fh['level'] . "\n";
    echo '  <label for="tag">태그</label>' . $fh['tag'] . "\n";
    echo $fh['hidden'];
    echo '  <button type="submit">검색</button>' . "\n";
    echo '  <a href="search.php">초기화</a>' . "\n";
    echo "</form>\n";
}

/**
 * 결과 표 출력
 *   <td> 순서: id, title, unit(code), level, tags(쉼표 구분)
 */
function renderResultTable(array $built, array $result)
{
    $total = $result['total'];
    $rows  = $result['rows'];

    echo $built['warning_html'];
    echo $built['summary_html'];

    echo '<p id="count">검색 결과 ' . (int)$total . '건</p>' . "\n";

    if ($total === 0) {
        echo '<p id="message">검색 결과가 없습니다</p>' . "\n";
        return;
    }

    $links = $built['sort_links'];
    echo '<table id="items">' . "\n";
    echo "<thead>\n<tr>";
    echo '<th>' . $links['id'] . '</th>';
    echo '<th>' . $links['title'] . '</th>';
    echo '<th>' . $links['unit'] . '</th>';
    echo '<th>' . $links['level'] . '</th>';
    echo '<th>' . $links['tags'] . '</th>';
    echo "</tr>\n</thead>\n";
    echo "<tbody>\n";
    foreach ($rows as $r) {
        echo '<tr>';
        echo '<td>' . h($r['id']) . '</td>';
        echo '<td>' . h($r['title']) . '</td>';
        echo '<td>' . h($r['unit_code']) . '</td>';
        echo '<td>' . h($r['level']) . '</td>';
        echo '<td>' . h($r['tag_names'] === null ? '' : $r['tag_names']) . '</td>';
        echo "</tr>\n";
    }
    echo "</tbody>\n</table>\n";

    // 페이지 이동
    $pageSize = $built['page_size'];
    $pages = (int)ceil($total / $pageSize);
    if ($pages > 1) {
        $cur = $built['page'];
        echo '<p class="pager">' . "\n";
        if ($cur > 1) {
            echo '  <a href="' . h($built['page_prefix'] . ($cur - 1)) . '">이전</a>' . "\n";
        }
        for ($p = 1; $p <= $pages; $p++) {
            if ($p === $cur) {
                echo '  <strong>' . $p . '</strong>' . "\n";
            } else {
                echo '  <a href="' . h($built['page_prefix'] . $p) . '">' . $p . '</a>' . "\n";
            }
        }
        if ($cur < $pages) {
            echo '  <a href="' . h($built['page_prefix'] . ($cur + 1)) . '">다음</a>' . "\n";
        }
        echo "</p>\n";
    }
}

// ----------------------------------------------------------------------
// 실행
// ----------------------------------------------------------------------
render_header('문항 검색', 'search');
echo '<h2>문항 검색</h2>' . "\n";

try {
    $conn = db_connect();
} catch (RuntimeException $e) {
    echo '<p class="error">' . h($e->getMessage()) . "</p>\n";
    echo '<p>DB가 준비되는 중일 수 있습니다. 잠시 후 새로 고침하세요.</p>' . "\n";
    render_footer();
    exit;
}

try {
    $built  = buildSearchQuery($_GET, $conn);
    renderSearchForm($built);
    $result = runSearchQuery($built, $conn);
    renderResultTable($built, $result);
} catch (RuntimeException $e) {
    Log::error('search failed', array('msg' => $e->getMessage()));
    echo '<p class="error">검색 중 오류가 발생했습니다.</p>' . "\n";
}

render_footer();
