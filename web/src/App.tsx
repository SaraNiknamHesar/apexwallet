import { Navigate, Route, Routes } from 'react-router-dom'
import { AccountsPage } from './pages/AccountsPage'
import { ApprovalsPage } from './pages/ApprovalsPage'
import { CardsPage } from './pages/CardsPage'
import { LoansPage } from './pages/LoansPage'
import { ChecksPage, FilesPage, PricingPage } from './pages/ModulesPage'
import { AssistantPage } from './pages/AssistantPage'
import { BookPage } from './pages/BookPage'
import { DeskPage } from './pages/DeskPage'
import { LoginPage } from './pages/LoginPage'
import { ServicesPage } from './pages/ServicesPage'
import { SettingPage } from './pages/SettingPage'
import { StatementPage } from './pages/StatementPage'
import { TransferPage } from './pages/TransferPage'
import { Shell, RequireRoles } from './shell'

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Shell />}>
        <Route path="/desk" element={<DeskPage />} />
        <Route path="/statement" element={<StatementPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/loans" element={<LoansPage />} />
        <Route path="/cards" element={<CardsPage />} />
        <Route path="/transfer" element={<TransferPage />} />
        <Route path="/approvals" element={<RequireRoles roles={['SUPERVISOR']}><ApprovalsPage /></RequireRoles>} />
        <Route path="/services" element={<ServicesPage />} />
        <Route path="/setting" element={<SettingPage />} />
        <Route path="/pricing" element={<PricingPage />} />
        <Route path="/checks" element={<ChecksPage />} />
        <Route path="/files" element={<FilesPage />} />
        <Route path="/assistant" element={<AssistantPage />} />
        <Route path="/book" element={<BookPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/desk" replace />} />
    </Routes>
  )
}
