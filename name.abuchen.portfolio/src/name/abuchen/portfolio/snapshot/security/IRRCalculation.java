package name.abuchen.portfolio.snapshot.security;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import name.abuchen.portfolio.math.IRR;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.Values;

/* package */class IRRCalculation extends Calculation
{
    private List<LocalDate> dates = new ArrayList<LocalDate>();
    private List<Double> values = new ArrayList<Double>();

    @Override
    public void visit(DividendInitialTransaction t)
    {
        dates.add(t.getDate());
        values.add(-t.getAmount() / Values.Amount.divider());
    }

    @Override
    public void visit(DividendFinalTransaction t)
    {
        dates.add(t.getDate());
        values.add(t.getAmount() / Values.Amount.divider());
    }

    @Override
    public void visit(DividendTransaction t)
    {
        dates.add(t.getDate());
        values.add(t.getAmount() / Values.Amount.divider());
    }

    @Override
    public void visit(AccountTransaction t)
    {
        // ignore tax refunds when calculating the irr for a single security
    }

    @Override
    public void visit(PortfolioTransaction t)
    {
        dates.add(t.getDate());
        long taxes = t.getUnitSum(Unit.Type.TAX).getAmount();
        switch (t.getType())
        {
            case BUY:
            case DELIVERY_INBOUND:
            case TRANSFER_IN:
                values.add((-t.getAmount() + taxes) / Values.Amount.divider());
                break;
            case SELL:
            case DELIVERY_OUTBOUND:
            case TRANSFER_OUT:
                values.add((t.getAmount() + taxes) / Values.Amount.divider());
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    public double getIRR()
    {
        return IRR.calculate(dates, values);
    }
}