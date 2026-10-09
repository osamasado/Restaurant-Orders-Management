import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { listCategories, listMeals, setMealAvailability } from '../../../../api/menuApi'
import type { AdminOrderRow, CategoryResponse, MealResponse } from '../../../../api/types'
import { CategoryChips } from '../../../../components/admin/CategoryChips'
import type { CategoryChip } from '../../../../components/admin/CategoryChips'
import { EmptyState } from '../../../../components/admin/EmptyState'
import { MealCard } from '../../../../components/admin/MealCard'
import { OrderPanel } from '../../../../components/admin/OrderPanel'
import { OrderReports } from '../../../../components/admin/OrderReports'
import { SearchBar } from '../../../../components/admin/SearchBar'
import { Modal } from '../../../../components/Modal'
import { Slider } from '../../../../components/Slider'
import { useToast } from '../../../../components/toast-context'
import { useLanguage } from '../../../../i18n/language-context'
import { useT } from '../../../../i18n/useT'
import { isolate } from '../../../../lib/bidi'
import { formatMoney } from '../../../../lib/formatMoney'
import { formatOrderNumber } from '../../../../lib/formatOrderNumber'
import { useMediaQuery } from '../../../../lib/useMediaQuery'
import {
  categoryName,
  lowestPrice,
  mealDescription,
  mealName,
  mealSearchText,
  toBackendLanguage,
} from '../../../../lib/translations'
import { useAdminOrders } from '../orders/useAdminOrders'
import type { OrdersFilter } from '../orders/useAdminOrders'
import { useOrderActions } from '../orders/useOrderActions'
import './DashboardView.css'

const ALL_ORDERS: OrdersFilter = { status: null, date: '' }
/** Without a search the dashboard shows the newest few orders; the full list is one link away. */
const ORDERS_SHOWN = 8

function orderMatches(order: AdminOrderRow, query: string): boolean {
  const haystack = [
    formatOrderNumber(order.orderNumber),
    String(order.orderNumber),
    order.tableNumber,
    ...order.items.map((item) => item.name),
  ]
    .join(' ')
    .toLowerCase()
  return haystack.includes(query)
}

/**
 * The admin's overview: the categories and meals of the menu, the newest orders, and a panel for the order being
 * looked at. Everything is the real data the other pages use (the menu, the live order list, the same actions);
 * the search only narrows what is already loaded. Wide screens show the order panel beside the page, narrower
 * ones open it as a dialog when an order is chosen.
 */
export function DashboardView() {
  const { t } = useT()
  const { language } = useLanguage()
  const backendLanguage = toBackendLanguage(language)
  const toast = useToast()
  const wide = useMediaQuery('(min-width: 1200px)')

  const [categories, setCategories] = useState<CategoryResponse[]>([])
  const [meals, setMeals] = useState<MealResponse[]>([])
  const [menuState, setMenuState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [busyMealId, setBusyMealId] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState<number | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)

  const { orders, connectionLost, refresh } = useAdminOrders(ALL_ORDERS)
  const actions = useOrderActions(refresh)

  const loadMenu = useCallback(() => {
    setMenuState('loading')
    Promise.all([listCategories(), listMeals()])
      .then(([categoryList, mealList]) => {
        setCategories(categoryList)
        setMeals(mealList)
        setMenuState('ready')
      })
      .catch(() => setMenuState('error'))
  }, [])

  // Fetch-on-mount, like the other admin pages: this project has no data library.
  // eslint-disable-next-line react-hooks/set-state-in-effect
  useEffect(() => loadMenu(), [loadMenu])

  const needle = query.trim().toLowerCase()
  const sortedCategories = useMemo(() => categories.slice().sort((a, b) => a.sortOrder - b.sortOrder), [categories])
  const categoryNames = useMemo(
    () => new Map(categories.map((category) => [category.id, categoryName(category, backendLanguage)])),
    [categories, backendLanguage],
  )

  const chips: CategoryChip[] = useMemo(
    () => [
      { id: null, name: t('admin.dashboard.allCategories'), count: meals.length, imageUrl: null },
      ...sortedCategories.map((category) => {
        const inCategory = meals.filter((meal) => meal.categoryId === category.id)
        return {
          id: category.id,
          name: categoryName(category, backendLanguage),
          count: inCategory.length,
          imageUrl: inCategory.find((meal) => meal.imageUrl)?.imageUrl ?? null,
        }
      }),
    ],
    [meals, sortedCategories, backendLanguage, t],
  )

  const matchingMeals = meals.filter(
    (meal) => (categoryId === null || meal.categoryId === categoryId) && (needle === '' || mealSearchText(meal).includes(needle)),
  )
  const shownMeals = matchingMeals

  const matchingOrders = (orders ?? []).filter((order) => needle === '' || orderMatches(order, needle))
  const shownOrders = needle !== '' ? matchingOrders : matchingOrders.slice(0, ORDERS_SHOWN)

  // On a wide screen the panel always shows an order (the newest until one is chosen); on a narrow one it opens on a choice.
  const selectedOrder =
    (orders ?? []).find((order) => order.orderId === selectedId) ?? (wide && selectedId === null ? (shownOrders[0] ?? null) : null)

  // An order line stores the meal's name as it was when ordered, not a link to the meal, so its photo is found by
  // name among every translation of every meal. A meal renamed or deleted since has no match and shows a plate.
  const photoByName = useMemo(() => {
    const map = new Map<string, string>()
    for (const meal of meals) {
      if (!meal.imageUrl) continue
      for (const translation of meal.translations) {
        const key = translation.name.trim().toLowerCase()
        if (!map.has(key)) map.set(key, meal.imageUrl)
      }
    }
    return map
  }, [meals])
  const imageFor = (mealName: string) => photoByName.get(mealName.trim().toLowerCase()) ?? null

  const rangeLabel = (from: number, to: number, total: number) => t('common.slider.range', { from, to, total })

  const formatPrice = (meal: MealResponse): string | null => {
    const price = lowestPrice(meal)
    if (price === null) return null
    const settings = actions.settings
    return settings
      ? formatMoney(price, language, settings.currencySymbol, settings.symbolPosition)
      : actions.formatTotal(price)
  }

  const handleToggleAvailability = async (meal: MealResponse) => {
    setBusyMealId(meal.id)
    try {
      const updated = await setMealAvailability(meal.id, !meal.available)
      setMeals((previous) => previous.map((existing) => (existing.id === updated.id ? updated : existing)))
    } catch {
      toast.show('error', t('admin.meals.availabilityError'))
    } finally {
      setBusyMealId(null)
    }
  }

  const panel = (headless: boolean) => (
    <OrderPanel
      order={selectedOrder}
      headless={headless}
      imageFor={imageFor}
      formatTime={actions.formatTime}
      formatTotal={actions.formatTotal}
      busy={selectedOrder !== null && actions.busyOrderId === selectedOrder.orderId}
      confirmingCancel={selectedOrder !== null && actions.confirmingOrderId === selectedOrder.orderId}
      notice={actions.notice}
      onAdvance={actions.handleAdvance}
      onAskCancel={actions.askCancel}
      onConfirmCancel={actions.handleConfirmCancel}
      onKeep={actions.keepOrder}
    />
  )

  return (
    <div className="dashboard">
      <div className="dashboard__main">
        <header className="dashboard__header">
          <div>
            <h2 className="dashboard__title">{t('admin.nav.dashboard')}</h2>
            <p className="dashboard__subtitle">{t('admin.dashboard.subtitle')}</p>
          </div>
          <SearchBar
            value={query}
            onChange={setQuery}
            label={t('admin.dashboard.searchLabel')}
            placeholder={t('admin.dashboard.searchPlaceholder')}
          />
        </header>

        <div className="dashboard__section">
          {menuState === 'ready' && sortedCategories.length > 0 ? (
            <CategoryChips
              heading={
                <h3 className="dashboard__section-title" id="dashboard-categories">
                  {t('admin.categories.title')}
                </h3>
              }
              chips={chips}
              selectedId={categoryId}
              onSelect={setCategoryId}
              label={t('admin.categories.title')}
              countLabel={(count) => t('admin.dashboard.mealCount', { count })}
              rangeLabel={rangeLabel}
            />
          ) : (
            <section aria-labelledby="dashboard-categories">
              <h3 className="dashboard__section-title" id="dashboard-categories">
                {t('admin.categories.title')}
              </h3>
              {menuState === 'ready' && <EmptyState icon="meals" title={t('admin.categories.empty')} />}
              {menuState === 'loading' && (
                <p className="dashboard__status" role="status">
                  {t('admin.dashboard.loading')}
                </p>
              )}
            </section>
          )}
        </div>

        <div className="dashboard__section">
          {shownMeals.length > 0 ? (
            <Slider
              heading={
                <h3 className="dashboard__section-title" id="dashboard-meals">
                  {t('admin.nav.meals')}
                </h3>
              }
              actions={
                <Link className="dashboard__link" to="/admin/meals">
                  {t('admin.dashboard.manageMeals')}
                </Link>
              }
              label={t('admin.nav.meals')}
              minItemWidth={230}
              gap={16}
              rangeLabel={rangeLabel}
            >
              {shownMeals.map((meal) => (
                <MealCard
                  key={meal.id}
                  name={mealName(meal, backendLanguage)}
                  description={mealDescription(meal, backendLanguage)}
                  imageUrl={meal.imageUrl}
                  categoryName={categoryNames.get(meal.categoryId) ?? ''}
                  price={formatPrice(meal)}
                  available={meal.available}
                  busy={busyMealId === meal.id}
                  onToggleAvailability={() => void handleToggleAvailability(meal)}
                />
              ))}
            </Slider>
          ) : (
            <section aria-labelledby="dashboard-meals">
              <div className="dashboard__section-header">
                <h3 className="dashboard__section-title" id="dashboard-meals">
                  {t('admin.nav.meals')}
                </h3>
                <Link className="dashboard__link" to="/admin/meals">
                  {t('admin.dashboard.manageMeals')}
                </Link>
              </div>
              {menuState === 'error' && (
                <EmptyState
                  tone="error"
                  icon="meals"
                  title={t('admin.dashboard.menuError')}
                  action={
                    <button type="button" className="button button--secondary button--small empty-state__action" onClick={loadMenu}>
                      {t('admin.dashboard.retry')}
                    </button>
                  }
                />
              )}
              {menuState === 'ready' && meals.length === 0 && <EmptyState icon="meals" title={t('admin.meals.empty')} />}
              {menuState === 'ready' && meals.length > 0 && (
                <EmptyState icon="search" title={t('admin.dashboard.noMealResults')} hint={t('admin.dashboard.noResultsHint')} />
              )}
              {menuState === 'loading' && (
                <p className="dashboard__status" role="status">
                  {t('admin.dashboard.loading')}
                </p>
              )}
            </section>
          )}
        </div>

        <section className="dashboard__section" aria-labelledby="dashboard-orders">
          <div className="dashboard__section-header">
            <h3 className="dashboard__section-title" id="dashboard-orders">
              {t('admin.dashboard.orderReports')}
            </h3>
            <Link className="dashboard__link" to="/admin/orders">
              {t('admin.dashboard.allOrders')}
            </Link>
          </div>
          {connectionLost && orders !== null && <p className="dashboard__notice">{t('admin.orders.connectionLost')}</p>}
          {connectionLost && orders === null && (
            <EmptyState tone="error" icon="orders" title={t('admin.orders.loadError')} />
          )}
          {orders === null && !connectionLost && (
            <p className="dashboard__status" role="status">
              {t('admin.dashboard.loading')}
            </p>
          )}
          {orders !== null && orders.length === 0 && <EmptyState icon="orders" title={t('admin.orders.empty')} />}
          {orders !== null && orders.length > 0 && shownOrders.length === 0 && (
            <EmptyState icon="search" title={t('admin.dashboard.noOrderResults')} hint={t('admin.dashboard.noResultsHint')} />
          )}
          {shownOrders.length > 0 && (
            <OrderReports
              orders={shownOrders}
              selectedId={selectedOrder?.orderId ?? null}
              onSelect={(order) => setSelectedId(order.orderId)}
              formatTime={actions.formatTime}
              formatTotal={actions.formatTotal}
            />
          )}
        </section>
      </div>

      {wide && <div className="dashboard__side">{panel(false)}</div>}
      {!wide && selectedOrder !== null && (
        <Modal
          title={t('admin.dashboard.panel.orderTitle', { number: isolate(formatOrderNumber(selectedOrder.orderNumber)) })}
          onClose={() => setSelectedId(null)}
        >
          {panel(true)}
        </Modal>
      )}
    </div>
  )
}
