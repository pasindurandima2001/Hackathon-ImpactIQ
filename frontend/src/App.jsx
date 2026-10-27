import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import RequireAuth from './components/RequireAuth.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import DefectsPage from './pages/DefectsPage.jsx'
import DefectFormPage from './pages/DefectFormPage.jsx'
import DefectDetailPage from './pages/DefectDetailPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import ProjectKnowledgePage from './pages/ProjectKnowledgePage.jsx'
import ChatbotPage from './pages/ChatbotPage.jsx'

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth><Layout /></RequireAuth>}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/defects" element={<DefectsPage />} />
          <Route path="/defects/new" element={<DefectFormPage />} />
          <Route path="/defects/:id" element={<DefectDetailPage />} />
          <Route path="/knowledge" element={<RequireAuth adminOnly><ProjectKnowledgePage /></RequireAuth>} />
          <Route path="/chat" element={<ChatbotPage />} />
          <Route path="/chat/:conversationId" element={<ChatbotPage />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Routes>
    </AuthProvider>
  )
}
