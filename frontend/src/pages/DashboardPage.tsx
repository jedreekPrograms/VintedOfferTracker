interface DashboardStat {
    label: string;
    value: number;
    description: string;
}

const dashboardStats: DashboardStat[] = [
    {
        label: "Aktywne boty",
        value: 0,
        description: "Boty ze statusem RUNNING",
    },
    {
        label: "Trwające negocjacje",
        value: 0,
        description: "Oferty obsługiwane przez boty"
    },
    {
        label: "Oferty do kupienia",
        value: 0,
        description: "Oferty ze statusem ACTION_REQUIRED",
    },
    {
        label: "Zakończone negocjacje",
        value: 0,
        description: "Kupione lub ręcznie zamknięte"
    }
];

function DashboardPage() {
    return (
        
    )

}

export default DashboardPage;