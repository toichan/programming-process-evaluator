const { test, expect } = require('@playwright/test');
const { assertRejectedLogin, login, requiredEnv } = require('./helpers');

async function clickFolderHeaderPadding(page, header) {
  await header.scrollIntoViewIfNeeded();
  const headerBox = await header.boundingBox();
  if (!headerBox) throw new Error('Folder header is not rendered');

  const point = {
    x: headerBox.x + headerBox.width - 48,
    y: headerBox.y + headerBox.height - 8
  };
  const controls = await header.locator('button, input, a').evaluateAll(elements =>
    elements.map(element => {
      const rect = element.getBoundingClientRect();
      return { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom };
    })
  );
  if (controls.some(rect => point.x >= rect.left && point.x <= rect.right
      && point.y >= rect.top && point.y <= rect.bottom)) {
    throw new Error('The selected folder-row click point overlaps an interactive control');
  }

  await page.mouse.click(point.x, point.y);
}

test('student can expand and collapse a folder from its row hit area', async ({ page }) => {
  const studentLogin = requiredEnv('PPE_P20_STUDENT_LOGIN');
  const studentPassword = requiredEnv('PPE_P20_STUDENT_PASSWORD');
  await assertRejectedLogin(page, '/student/account/login', studentLogin, 'invalid-synthetic-password');
  await login(page, '/student/account/login', studentLogin, studentPassword, '/student/home');

  await page.goto('/student/exercise');
  await expect(page.getByRole('heading', { name: '授業演習', exact: true })).toBeVisible();
  const folderButton = page.getByRole('button', { name: '基礎', exact: true });
  const folderHeader = page.locator('.lesson-tree-folder-header')
    .filter({ has: folderButton });
  const folderToggle = folderHeader.getByRole('button', { name: '基礎の展開・折りたたみ' });
  const folderActions = folderHeader.getByRole('button', { name: '基礎の操作' });
  const childFile = page.getByRole('button', { name: 'hello.py', exact: true });
  const mutations = [];
  page.on('request', request => {
    if (!['GET', 'HEAD'].includes(request.method())) mutations.push(request.method());
  });

  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(folderHeader).toHaveCSS('flex-direction', 'row');
    await expect(folderToggle).toBeVisible();
    await expect(folderActions).toBeVisible();
    const headerBounds = await folderHeader.boundingBox();
    expect(headerBounds.height).toBeLessThanOrEqual(52);
    await expect.poll(async () => {
      const centers = await folderHeader.locator('button').evaluateAll(elements =>
        elements.filter(element => element.getClientRects().length > 0).map(element => {
          const bounds = element.getBoundingClientRect();
          return bounds.y + bounds.height / 2;
        })
      );
      expect(centers).toHaveLength(3);
      return Math.max(...centers) - Math.min(...centers);
    }).toBeLessThanOrEqual(1);
  }

  await page.setViewportSize({ width: 390, height: 844 });

  await expect(folderButton).toHaveAttribute('aria-expanded', 'false');
  await clickFolderHeaderPadding(page, folderHeader);
  await expect(folderButton).toHaveAttribute('aria-expanded', 'true');
  await expect(childFile).toBeVisible();

  await clickFolderHeaderPadding(page, folderHeader);
  await expect(folderButton).toHaveAttribute('aria-expanded', 'false');
  await expect(childFile).toBeHidden();

  await folderButton.click();
  await expect(folderButton).toHaveAttribute('aria-expanded', 'true');
  await expect(childFile).toBeVisible();

  await page.reload();
  await expect(page.getByRole('button', { name: '基礎', exact: true }))
    .toHaveAttribute('aria-expanded', 'false');
  await expect(page.getByRole('button', { name: 'hello.py', exact: true })).toBeHidden();
  expect(mutations).toEqual([]);
});
